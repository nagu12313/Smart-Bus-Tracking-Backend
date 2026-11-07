package com.smartbus.service.impl;

import com.smartbus.dto.request.LocationUpdateRequest;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.entity.Bus;
import com.smartbus.entity.BusLatestLocation;
import com.smartbus.entity.BusLocation;
import com.smartbus.entity.BusLocationHistory;
import com.smartbus.entity.Driver;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.BusLatestLocationRepository;
import com.smartbus.repository.BusLocationHistoryRepository;
import com.smartbus.repository.BusLocationRepository;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.TripRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
 * High-Performance GPS Tracking Service.
 * Uses an ultra-fast in-memory ConcurrentHashMap cache for sub-millisecond retrieval,
 * backed by persistent storage in Aiven Cloud MySQL.
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

    private static final long STALENESS_THRESHOLD_SECONDS = 120; // 2 minutes (120 seconds)

    // High-speed In-Memory Cache for sub-millisecond live tracking responses
    private final Map<Long, LocationResponse> liveCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warmupCache() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                // First try hot table bus_latest_locations
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
     * High-speed GPS record insertion: writes to MySQL and updates live cache instantly.
     */
    @Transactional
    public LocationResponse recordLocation(LocationUpdateRequest request, String deviceToken) {
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));

        // Validate device token if the bus has one configured
        if (bus.getGpsDeviceToken() != null && !bus.getGpsDeviceToken().isBlank()) {
            if (deviceToken == null || !deviceToken.equals(bus.getGpsDeviceToken())) {
                throw new BadRequestException("Invalid GPS device token for bus: " + request.getBusId());
            }
        }

        LocalDateTime gpsTime = request.getGpsTime() != null
                ? request.getGpsTime()
                : LocalDateTime.now();

        BusLocation location = BusLocation.builder()
                .bus(bus)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(request.getSpeed() != null ? request.getSpeed() : 0.0)
                .heading(request.getHeading())
                .gpsTime(gpsTime)
                .build();

        // Link to active trip if tripId provided or find one
        if (request.getTripId() != null) {
            tripRepository.findById(request.getTripId())
                    .ifPresent(location::setTrip);
        } else {
            tripRepository.findTopByBusIdAndStatusOrderByCreatedAtDesc(bus.getId(), "IN_PROGRESS")
                    .ifPresent(location::setTrip);
        }

        busLocationRepository.save(location);

        // Update bus status to ON_ROUTE if moving
        if (request.getSpeed() != null && request.getSpeed() > 2.0) {
            bus.setStatus("ON_ROUTE");
        } else if (request.getSpeed() != null && request.getSpeed() <= 2.0) {
            bus.setStatus("AT_STOP");
        }
        busRepository.save(bus);

        // Synchronize hot table (bus_latest_locations) so restarts reload correct data
        try {
            BusLatestLocation latest = busLatestLocationRepository.findByBusId(bus.getId())
                    .orElse(BusLatestLocation.builder().bus(bus).build());
            latest.setLatitude(request.getLatitude());
            latest.setLongitude(request.getLongitude());
            latest.setSpeed(request.getSpeed() != null ? request.getSpeed() : 0.0);
            latest.setHeading(request.getHeading() != null ? request.getHeading() : 0.0);
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

        LocationResponse response = toResponse(bus, location);
        // Instant in-memory cache update
        liveCache.put(bus.getId(), response);

        try {
            org.springframework.messaging.simp.SimpMessagingTemplate messaging = messagingTemplateProvider.getIfAvailable();
            if (messaging != null) {
                messaging.convertAndSend("/topic/tracking", response);
                messaging.convertAndSend("/topic/bus/" + bus.getId(), response);
            }
        } catch (Exception ex) {
            log.debug("WebSocket broadcast skipped: {}", ex.getMessage());
        }

        log.debug("GPS location recorded: busId={}, lat={}, lng={}", bus.getId(),
                request.getLatitude(), request.getLongitude());
        return response;
    }

    /**
     * Sub-millisecond lookup from in-memory cache, falls back to DB if missing.
     */
    @Transactional(readOnly = true)
    public LocationResponse getLatestLocation(Long busId) {
        LocationResponse cached = liveCache.get(busId);
        if (cached != null) {
            // refresh stale flag dynamically
            return checkStale(cached);
        }

        // Try hot table bus_latest_locations
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
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus", busId));

        // First check bus_location_history
        List<BusLocationHistory> history = busLocationHistoryRepository
                .findLatestHistoryByBusId(busId, PageRequest.of(0, limit));
        if (history != null && !history.isEmpty()) {
            return history.stream()
                    .map(h -> toResponse(bus, h))
                    .collect(Collectors.toList());
        }

        // Fallback to legacy bus_locations table
        return busLocationRepository.findHistoryByBusId(busId, PageRequest.of(0, limit))
                .stream()
                .map(loc -> toResponse(bus, loc))
                .collect(Collectors.toList());
    }

    /**
     * High-speed retrieval for map polling: served directly from RAM!
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getAllLatestLocations() {
        if (!liveCache.isEmpty()) {
            return liveCache.values().stream()
                    .map(this::checkStale)
                    .collect(Collectors.toList());
        }

        List<BusLatestLocation> latest = busLatestLocationRepository.findAllWithBusDetails();
        if (latest != null && !latest.isEmpty()) {
            List<LocationResponse> fresh = latest.stream()
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

    /**
     * Retrieve all active bus locations (filtered by non-OFFLINE status).
     */
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

        return LocationResponse.builder()
                .busId(bus != null ? bus.getId() : null)
                .busNumber(bus != null ? bus.getBusNumber() : "N/A")
                .busName(bus != null ? bus.getBusName() : "Campus Bus")
                .status(liveStatus)
                .latitude(bll.getLatitude())
                .longitude(bll.getLongitude())
                .speed(liveSpeed)
                .heading(bll.getHeading())
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
                .speed(liveSpeed)
                .heading(blh.getHeading())
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

        return LocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .status(liveStatus)
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .speed(liveSpeed)
                .heading(loc.getHeading())
                .gpsTime(loc.getGpsTime())
                .receivedAt(loc.getCreatedAt())
                .tripId(loc.getTrip() != null ? loc.getTrip().getId() : null)
                .driverId(driverId)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .isStale(isStale)
                .build();
    }
}
