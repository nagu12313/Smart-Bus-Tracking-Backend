package com.smartbus.service;

import com.smartbus.dto.request.GpsLocationRequest;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.entity.*;
import com.smartbus.exception.BadRequestException;
import com.smartbus.repository.*;
import com.smartbus.service.impl.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * GpsIngestionService — Industrial ingestion pipeline for physical GPS tracking hardware.
 * Ingests, validates, updates hot table (O(1)), cold trail logs, and in-memory cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpsIngestionService {

    private final GpsDeviceAuthenticationService authService;
    private final BusRepository busRepository;
    private final TripRepository tripRepository;
    private final BusLatestLocationRepository busLatestLocationRepository;
    private final BusLocationHistoryRepository busLocationHistoryRepository;
    private final BusLocationRepository busLocationRepository;
    private final TrackingService trackingService;

    @Transactional
    public LocationResponse ingestLocation(GpsLocationRequest request, String deviceToken) {
        // Step 1: Authenticate physical GPS hardware device
        GpsDevice device = authService.authenticateDevice(request.getDeviceId(), deviceToken);

        // Step 2: Validate coordinate integrity
        validateCoordinates(request);

        // Step 3: Validate timestamp skew
        LocalDateTime gpsTime = (request.getTimestamp() != null)
                ? request.getTimestamp()
                : LocalDateTime.now();

        if (ChronoUnit.MINUTES.between(LocalDateTime.now(), gpsTime) > 5) {
            throw new BadRequestException("GPS timestamp is in the future. Clock skew exceeds allowed threshold.");
        }

        // Step 4: Resolve assigned vehicle
        Bus bus = device.getBus();
        if (bus == null) {
            throw new BadRequestException("No bus assigned to GPS tracker: " + device.getDeviceIdentifier());
        }

        // Step 5: Check active trip
        Trip activeTrip = tripRepository
                .findTopByBusIdAndStatusOrderByCreatedAtDesc(bus.getId(), "IN_PROGRESS")
                .orElse(null);

        double speed = (request.getSpeed() != null) ? Math.max(0.0, request.getSpeed()) : 0.0;
        double heading = (request.getHeading() != null) ? request.getHeading() : 0.0;
        double accuracy = (request.getAccuracy() != null) ? request.getAccuracy() : 5.0;
        double altitude = (request.getAltitude() != null) ? request.getAltitude() : 0.0;
        boolean ignition = (request.getIgnitionOn() != null) ? request.getIgnitionOn() : true;

        // Step 6: Determine vehicle status
        String vehicleStatus = (speed > 2.0) ? "ON_ROUTE" : (activeTrip != null ? "AT_STOP" : "ONLINE");
        bus.setStatus(vehicleStatus);
        busRepository.save(bus);

        // Step 7: Hot Table Upsert (bus_latest_locations) - Exactly 1 row per bus
        BusLatestLocation latest = busLatestLocationRepository.findByBusId(bus.getId())
                .orElse(BusLatestLocation.builder().bus(bus).build());

        latest.setDevice(device);
        latest.setTrip(activeTrip);
        latest.setLatitude(request.getLatitude());
        latest.setLongitude(request.getLongitude());
        latest.setSpeed(speed);
        latest.setHeading(heading);
        latest.setAccuracy(accuracy);
        latest.setAltitude(altitude);
        latest.setIgnitionOn(ignition);
        latest.setGpsTimestamp(gpsTime);
        latest.setReceivedAt(LocalDateTime.now());
        latest.setStatus(vehicleStatus);
        busLatestLocationRepository.save(latest);

        // Step 8: Cold Table Append (bus_location_history) - Breadcrumb trail log
        BusLocationHistory history = BusLocationHistory.builder()
                .bus(bus)
                .device(device)
                .trip(activeTrip)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(speed)
                .heading(heading)
                .accuracy(accuracy)
                .altitude(altitude)
                .gpsTimestamp(gpsTime)
                .receivedAt(LocalDateTime.now())
                .build();
        busLocationHistoryRepository.save(history);

        // Step 9: Legacy bus_locations sync (for full backwards-compatibility)
        BusLocation legacy = BusLocation.builder()
                .bus(bus)
                .trip(activeTrip)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(speed)
                .heading(heading)
                .gpsTime(gpsTime)
                .build();
        busLocationRepository.save(legacy);

        // Step 10: Assemble unified real-time response
        Driver driver = (activeTrip != null && activeTrip.getDriver() != null)
                ? activeTrip.getDriver()
                : bus.getDriver();

        String driverName = (driver != null && driver.getUser() != null)
                ? driver.getUser().getName()
                : "Assigned Driver";
        String driverPhone = (driver != null) ? driver.getPhone() : null;

        LocationResponse response = LocationResponse.builder()
                .busId(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .status(vehicleStatus)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(speed)
                .heading(heading)
                .gpsTime(gpsTime)
                .receivedAt(LocalDateTime.now())
                .tripId(activeTrip != null ? activeTrip.getId() : null)
                .driverId(driver != null ? driver.getId() : null)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .isStale(false)
                .build();

        log.debug("Ingested GPS packet: busId={}, device={}, lat={}, lng={}, speed={}",
                bus.getId(), device.getDeviceIdentifier(), request.getLatitude(), request.getLongitude(), speed);

        // Step 11: Update in-memory cache for instant zero-latency retrieval
        trackingService.updateLiveCache(bus.getId(), response);

        return response;
    }

    private void validateCoordinates(GpsLocationRequest req) {
        if (req.getLatitude() == null || req.getLongitude() == null) {
            throw new BadRequestException("Latitude and Longitude are mandatory");
        }
        if (req.getLatitude() < -90.0 || req.getLatitude() > 90.0) {
            throw new BadRequestException("Invalid latitude: " + req.getLatitude() + ". Must be between -90 and +90.");
        }
        if (req.getLongitude() < -180.0 || req.getLongitude() > 180.0) {
            throw new BadRequestException("Invalid longitude: " + req.getLongitude() + ". Must be between -180 and +180.");
        }
        // Reject Null Island (0.0, 0.0) coordinates
        if (Math.abs(req.getLatitude()) < 0.0001 && Math.abs(req.getLongitude()) < 0.0001) {
            throw new BadRequestException("Invalid coordinates: (0,0) Null Island rejected.");
        }
    }
}
