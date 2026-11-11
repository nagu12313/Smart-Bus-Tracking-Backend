package com.smartbus.service.impl;

import com.smartbus.dto.request.LocationUpdateRequest;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.entity.*;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.*;
import com.smartbus.service.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise GPS Tracking Service with High-Accuracy Validation,
 * Road Snapping, Dynamic Bearing Resolution, and Verified Reverse Geocoding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private final BusLocationRepository busLocationRepository;
    private final BusRepository busRepository;
    private final TripRepository tripRepository;
    private final BusLatestLocationRepository busLatestLocationRepository;
    private final BusLocationHistoryRepository busLocationHistoryRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.messaging.simp.SimpMessagingTemplate> messagingTemplateProvider;

    private final GpsValidationService gpsValidationService;
    private final RoadMapMatchingService roadMapMatchingService;
    private final BusHeadingService busHeadingService;
    private final ReverseGeocodingService reverseGeocodingService;

    // High-speed In-Memory Cache for sub-millisecond live tracking responses
    private final Map<Long, LocationResponse> liveCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warmupCache() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                List<BusLatestLocation> latest = busLatestLocationRepository.findAllWithBusDetails();
                if (latest != null && !latest.isEmpty()) {
                    for (BusLatestLocation bll : latest) {
                        if (bll.getBus() != null) {
                            liveCache.put(bll.getBus().getId(), toResponse(bll));
                        }
                    }
                } else {
                    List<BusLocation> latestLocations = busLocationRepository.findLatestLocationForAllBuses();
                    for (BusLocation loc : latestLocations) {
                        if (loc.getBus() != null) {
                            liveCache.put(loc.getBus().getId(), toResponse(loc.getBus(), loc));
                        }
                    }
                }
            });
            log.info("Live GPS cache warmed up with {} active vehicle locations", liveCache.size());
        } catch (Exception e) {
            log.warn("Cache warmup will defer to first request: {}", e.getMessage());
        }
    }

    public void updateLiveCache(Long busId, LocationResponse response) {
        liveCache.put(busId, response);
        try {
            org.springframework.messaging.simp.SimpMessagingTemplate messaging = messagingTemplateProvider.getIfAvailable();
            if (messaging != null) {
                messaging.convertAndSend("/topic/tracking", response);
                messaging.convertAndSend("/topic/bus/" + busId, response);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * High-accuracy GPS record insertion with road matching and verified geocoding.
     */
    @Transactional
    public LocationResponse recordLocation(LocationUpdateRequest request, String deviceToken) {
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));

        // Validate device token if the bus has one configured, unless request is from an authenticated user (driver/admin)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticatedUser = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());

        if (!isAuthenticatedUser && bus.getGpsDeviceToken() != null && !bus.getGpsDeviceToken().isBlank()) {
            if (deviceToken == null || !deviceToken.equals(bus.getGpsDeviceToken())) {
                log.error("[GPS REJECTION] BusId: {}, Reason: Invalid GPS device token", bus.getId());
                throw new BadRequestException("Invalid GPS device token for bus: " + request.getBusId());
            }
        }

        LocationResponse prevLocation = liveCache.get(bus.getId());

        // 1. Strict GPS Validation (bounds, clock skew, accuracy filter, monotonic timestamp, coordinates reversal check)
        GpsValidationService.ValidationResult validation = gpsValidationService.validate(request, prevLocation);
        if (!validation.isValid()) {
            log.error("[GPS REJECTION] BusId: {}, Lat: {}, Lng: {}, Reason: {}",
                    bus.getId(), request.getLatitude(), request.getLongitude(), validation.getRejectionReason());
            throw new BadRequestException("Invalid GPS fix: " + validation.getRejectionReason());
        }

        double rawLat = request.getLatitude();
        double rawLng = request.getLongitude();
        double speed = validation.getSanitizedSpeed();
        double accuracy = validation.getSanitizedAccuracy();

        LocalDateTime gpsTime = request.getGpsTime() != null ? request.getGpsTime() : LocalDateTime.now();

        // 2. Road Map Matching & Snapping
        RoadMapMatchingService.MapMatchResult matchResult = roadMapMatchingService.matchToRoad(
                bus, rawLat, rawLng, accuracy
        );

        Double snappedLat = matchResult.isMatched() ? matchResult.getSnappedLatitude() : null;
        Double snappedLng = matchResult.isMatched() ? matchResult.getSnappedLongitude() : null;
        boolean isRoadMatched = matchResult.isMatched();
        String roadName = matchResult.getRoadName();

        // Display coordinates: use snapped coordinates for visual road alignment if match is reliable, otherwise preserve raw GPS
        double displayLat = isRoadMatched && snappedLat != null ? snappedLat : rawLat;
        double displayLng = isRoadMatched && snappedLng != null ? snappedLng : rawLng;

        // 3. Dynamic Heading Resolution (facing direction of movement)
        Double prevLat = prevLocation != null ? (prevLocation.getRawLatitude() != null ? prevLocation.getRawLatitude() : prevLocation.getLatitude()) : null;
        Double prevLng = prevLocation != null ? (prevLocation.getRawLongitude() != null ? prevLocation.getRawLongitude() : prevLocation.getLongitude()) : null;
        Double prevHeading = prevLocation != null ? prevLocation.getHeading() : null;

        Double heading = busHeadingService.resolveHeading(
                request.getHeading(),
                speed,
                displayLat,
                displayLng,
                prevLat,
                prevLng,
                prevHeading,
                matchResult.getRoadHeading()
        );

        // 4. Verified Reverse Geocoding with Strict Proximity Distinction
        ReverseGeocodingService.GeocodeResult geocode = reverseGeocodingService.reverseGeocode(rawLat, rawLng, accuracy);
        String locationName = geocode.getLocationName();
        String nearbyLandmark = geocode.getNearbyLandmark();
        if (roadName == null || roadName.isBlank()) {
            roadName = geocode.getRoadName();
        }

        // 5. Update bus status
        if (speed > 2.0) {
            bus.setStatus("ON_ROUTE");
        } else {
            bus.setStatus("AT_STOP");
        }
        busRepository.save(bus);

        // 6. Save Persistent BusLocation (Cold Log)
        BusLocation location = BusLocation.builder()
                .bus(bus)
                .latitude(rawLat)
                .longitude(rawLng)
                .snappedLatitude(snappedLat)
                .snappedLongitude(snappedLng)
                .speed(speed)
                .heading(heading)
                .accuracy(accuracy)
                .locationName(locationName)
                .nearbyLandmark(nearbyLandmark)
                .gpsTime(gpsTime)
                .build();

        if (request.getTripId() != null) {
            tripRepository.findById(request.getTripId()).ifPresent(location::setTrip);
        } else {
            tripRepository.findTopByBusIdAndStatusOrderByCreatedAtDesc(bus.getId(), "IN_PROGRESS").ifPresent(location::setTrip);
        }
        busLocationRepository.save(location);

        // 7. Synchronize Hot Table (bus_latest_locations)
        try {
            BusLatestLocation latest = busLatestLocationRepository.findByBusId(bus.getId())
                    .orElse(BusLatestLocation.builder().bus(bus).build());
            latest.setLatitude(rawLat);
            latest.setLongitude(rawLng);
            latest.setSnappedLatitude(snappedLat);
            latest.setSnappedLongitude(snappedLng);
            latest.setSpeed(speed);
            latest.setHeading(heading);
            latest.setAccuracy(accuracy);
            latest.setIsRoadMatched(isRoadMatched);
            latest.setRoadName(roadName);
            latest.setLocationName(locationName);
            latest.setNearbyLandmark(nearbyLandmark);
            latest.setGpsTimestamp(gpsTime);
            latest.setReceivedAt(LocalDateTime.now());
            latest.setStatus(bus.getStatus());
            if (location.getTrip() != null) {
                latest.setTrip(location.getTrip());
            }
            busLatestLocationRepository.save(latest);
        } catch (Exception e) {
            log.warn("Failed to update bus_latest_locations hot table: {}", e.getMessage());
        }

        // 8. Build Complete LocationResponse
        Driver driver = location.getTrip() != null && location.getTrip().getDriver() != null
                ? location.getTrip().getDriver()
                : bus.getDriver();

        LocationResponse response = LocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .status(bus.getStatus())
                .latitude(displayLat)
                .longitude(displayLng)
                .rawLatitude(rawLat)
                .rawLongitude(rawLng)
                .snappedLatitude(snappedLat)
                .snappedLongitude(snappedLng)
                .isRoadMatched(isRoadMatched)
                .roadName(roadName)
                .speed(speed)
                .heading(heading)
                .accuracy(accuracy)
                .accuracyDescription(validation.getAccuracyDescription())
                .provider(request.getProvider() != null ? request.getProvider() : "gps")
                .locationName(locationName)
                .nearbyLandmark(nearbyLandmark)
                .gpsTime(gpsTime)
                .receivedAt(LocalDateTime.now())
                .tripId(location.getTrip() != null ? location.getTrip().getId() : null)
                .driverId(driver != null ? driver.getId() : null)
                .driverName(driver != null && driver.getUser() != null ? driver.getUser().getName() : "Assigned Driver")
                .driverPhone(driver != null ? driver.getPhone() : null)
                .isStale(false)
                .build();

        // 9. Instant in-memory cache update & WebSocket broadcast
        updateLiveCache(bus.getId(), response);

        log.debug("GPS location recorded: busId={}, display=({}, {}), raw=({}, {}), roadMatched={}, place={}",
                bus.getId(), displayLat, displayLng, rawLat, rawLng, isRoadMatched, locationName);
        return response;
    }

    /**
     * Sub-millisecond lookup from in-memory cache, falls back to DB if missing.
     */
    @Transactional(readOnly = true)
    public LocationResponse getLatestLocation(Long busId) {
        LocationResponse cached = liveCache.get(busId);
        if (cached != null) {
            return checkStale(cached);
        }

        return busLatestLocationRepository.findByBusId(busId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    Bus bus = busRepository.findById(busId)
                            .orElseThrow(() -> new ResourceNotFoundException("Bus", busId));
                    BusLocation location = busLocationRepository.findLatestByBusId(busId)
                            .orElseGet(() -> busLocationRepository.findTopByBusIdOrderByGpsTimeDesc(busId)
                                    .orElseThrow(() -> new ResourceNotFoundException("No GPS data found for bus: " + busId)));
                    LocationResponse res = toResponse(bus, location);
                    liveCache.put(busId, res);
                    return res;
                });
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getLocationHistory(Long busId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);

        List<BusLocationHistory> coldHistory = busLocationHistoryRepository
                .findLatestHistoryByBusId(busId, PageRequest.of(0, safeLimit));

        if (coldHistory != null && !coldHistory.isEmpty()) {
            Bus bus = busRepository.findById(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bus", busId));
            return coldHistory.stream()
                    .map(h -> toResponse(bus, h))
                    .collect(Collectors.toList());
        }

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus", busId));
        return busLocationRepository
                .findHistoryByBusId(busId, PageRequest.of(0, safeLimit))
                .stream()
                .map(loc -> toResponse(bus, loc))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getAllLatestLocations() {
        if (!liveCache.isEmpty()) {
            return liveCache.values().stream()
                    .map(this::checkStale)
                    .collect(Collectors.toList());
        }

        List<BusLatestLocation> hotRows = busLatestLocationRepository.findAllWithBusDetails();
        if (hotRows != null && !hotRows.isEmpty()) {
            List<LocationResponse> fresh = hotRows.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            fresh.forEach(l -> liveCache.put(l.getBusId(), l));
            return fresh;
        }

        List<LocationResponse> fresh = busLocationRepository.findLatestLocationForAllBuses().stream()
                .map(loc -> toResponse(loc.getBus(), loc))
                .collect(Collectors.toList());

        fresh.forEach(l -> liveCache.put(l.getBusId(), l));
        return fresh;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getActiveBusesLocations() {
        return getAllLatestLocations().stream()
                .filter(l -> !"OFFLINE".equalsIgnoreCase(l.getStatus()))
                .collect(Collectors.toList());
    }

    public LocationResponse toResponse(BusLatestLocation bll) {
        Bus bus = bll.getBus();
        LocalDateTime refTime = bll.getReceivedAt() != null ? bll.getReceivedAt() : bll.getGpsTimestamp();
        boolean isStale = refTime == null
                || ChronoUnit.MINUTES.between(refTime, LocalDateTime.now()) > 3;

        Double liveSpeed = isStale ? 0.0 : (bll.getSpeed() != null ? bll.getSpeed() : 0.0);
        String liveStatus = isStale ? "PARKED" : (bll.getStatus() != null ? bll.getStatus() : (bus != null ? bus.getStatus() : "ONLINE"));

        Driver driver = bll.getTrip() != null && bll.getTrip().getDriver() != null
                ? bll.getTrip().getDriver()
                : (bus != null ? bus.getDriver() : null);

        Long driverId = driver != null ? driver.getId() : null;
        String driverName = driver != null && driver.getUser() != null ? driver.getUser().getName() : "Assigned Driver";
        String driverPhone = driver != null ? driver.getPhone() : null;

        boolean isRoadMatched = Boolean.TRUE.equals(bll.getIsRoadMatched());
        double displayLat = isRoadMatched && bll.getSnappedLatitude() != null ? bll.getSnappedLatitude() : bll.getLatitude();
        double displayLng = isRoadMatched && bll.getSnappedLongitude() != null ? bll.getSnappedLongitude() : bll.getLongitude();

        return LocationResponse.builder()
                .busId(bus != null ? bus.getId() : null)
                .busNumber(bus != null ? bus.getBusNumber() : "N/A")
                .busName(bus != null ? bus.getBusName() : "Campus Bus")
                .status(liveStatus)
                .latitude(displayLat)
                .longitude(displayLng)
                .rawLatitude(bll.getLatitude())
                .rawLongitude(bll.getLongitude())
                .snappedLatitude(bll.getSnappedLatitude())
                .snappedLongitude(bll.getSnappedLongitude())
                .isRoadMatched(isRoadMatched)
                .roadName(bll.getRoadName())
                .locationName(bll.getLocationName())
                .nearbyLandmark(bll.getNearbyLandmark())
                .speed(liveSpeed)
                .heading(bll.getHeading())
                .accuracy(bll.getAccuracy())
                .accuracyDescription(getAccuracyDesc(bll.getAccuracy()))
                .provider("gps")
                .gpsTime(bll.getGpsTimestamp())
                .receivedAt(bll.getReceivedAt())
                .tripId(bll.getTrip() != null ? bll.getTrip().getId() : null)
                .driverId(driverId)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .isStale(isStale)
                .build();
    }

    private LocationResponse toResponse(Bus bus, BusLocationHistory blh) {
        LocalDateTime refTime = blh.getReceivedAt() != null ? blh.getReceivedAt() : blh.getGpsTimestamp();
        boolean isStale = refTime == null
                || ChronoUnit.MINUTES.between(refTime, LocalDateTime.now()) > 3;

        Driver driver = blh.getTrip() != null && blh.getTrip().getDriver() != null
                ? blh.getTrip().getDriver()
                : bus.getDriver();

        Long driverId = driver != null ? driver.getId() : null;
        String driverName = driver != null && driver.getUser() != null ? driver.getUser().getName() : "Assigned Driver";
        String driverPhone = driver != null ? driver.getPhone() : null;

        Double liveSpeed = isStale ? 0.0 : (blh.getSpeed() != null ? blh.getSpeed() : 0.0);
        String liveStatus = isStale ? "PARKED" : (bus.getStatus() != null ? bus.getStatus() : "ONLINE");

        return LocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .status(liveStatus)
                .latitude(blh.getLatitude())
                .longitude(blh.getLongitude())
                .rawLatitude(blh.getLatitude())
                .rawLongitude(blh.getLongitude())
                .isRoadMatched(false)
                .speed(liveSpeed)
                .heading(blh.getHeading())
                .accuracy(blh.getAccuracy())
                .accuracyDescription(getAccuracyDesc(blh.getAccuracy()))
                .gpsTime(blh.getGpsTimestamp())
                .receivedAt(blh.getReceivedAt())
                .tripId(blh.getTrip() != null ? blh.getTrip().getId() : null)
                .driverId(driverId)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .isStale(isStale)
                .build();
    }

    private LocationResponse checkStale(LocationResponse loc) {
        LocalDateTime refTime = loc.getReceivedAt() != null ? loc.getReceivedAt() : loc.getGpsTime();
        boolean isStale = refTime == null
                || ChronoUnit.MINUTES.between(refTime, LocalDateTime.now()) > 3;
        loc.setIsStale(isStale);
        if (isStale) {
            loc.setSpeed(0.0);
            if (!"OFFLINE".equalsIgnoreCase(loc.getStatus())) {
                loc.setStatus("PARKED");
            }
        }
        return loc;
    }

    private LocationResponse toResponse(Bus bus, BusLocation loc) {
        LocalDateTime refTime = loc.getCreatedAt() != null ? loc.getCreatedAt() : loc.getGpsTime();
        boolean isStale = refTime == null
                || ChronoUnit.MINUTES.between(refTime, LocalDateTime.now()) > 3;

        Driver driver = loc.getTrip() != null && loc.getTrip().getDriver() != null
                ? loc.getTrip().getDriver()
                : bus.getDriver();

        Long driverId = driver != null ? driver.getId() : null;
        String driverName = driver != null && driver.getUser() != null ? driver.getUser().getName() : null;
        String driverPhone = driver != null ? driver.getPhone() : null;

        Double liveSpeed = isStale ? 0.0 : (loc.getSpeed() != null ? loc.getSpeed() : 0.0);
        String liveStatus = isStale ? "PARKED" : (bus.getStatus() != null ? bus.getStatus() : "ONLINE");

        double rawLat = loc.getLatitude();
        double rawLng = loc.getLongitude();
        Double snappedLat = loc.getSnappedLatitude();
        Double snappedLng = loc.getSnappedLongitude();
        boolean isRoadMatched = snappedLat != null && snappedLng != null;
        double displayLat = isRoadMatched ? snappedLat : rawLat;
        double displayLng = isRoadMatched ? snappedLng : rawLng;

        return LocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .status(liveStatus)
                .latitude(displayLat)
                .longitude(displayLng)
                .rawLatitude(rawLat)
                .rawLongitude(rawLng)
                .snappedLatitude(snappedLat)
                .snappedLongitude(snappedLng)
                .isRoadMatched(isRoadMatched)
                .locationName(loc.getLocationName())
                .nearbyLandmark(loc.getNearbyLandmark())
                .speed(liveSpeed)
                .heading(loc.getHeading())
                .accuracy(loc.getAccuracy())
                .accuracyDescription(getAccuracyDesc(loc.getAccuracy()))
                .gpsTime(loc.getGpsTime())
                .receivedAt(loc.getCreatedAt())
                .tripId(loc.getTrip() != null ? loc.getTrip().getId() : null)
                .driverId(driverId)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .isStale(isStale)
                .build();
    }

    private String getAccuracyDesc(Double acc) {
        if (acc == null) return "Standard (±10m)";
        if (acc <= 10.0) return String.format("High (±%.0fm)", acc);
        if (acc <= 30.0) return String.format("Good (±%.0fm)", acc);
        if (acc <= 60.0) return String.format("Moderate (±%.0fm)", acc);
        return String.format("Approximate (±%.0fm)", acc);
    }
}
