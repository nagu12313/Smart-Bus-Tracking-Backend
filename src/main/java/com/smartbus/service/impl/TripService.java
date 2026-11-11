package com.smartbus.service.impl;

import com.smartbus.dto.request.ReportRoadIssueRequest;
import com.smartbus.dto.request.RerouteRequest;
import com.smartbus.dto.request.TripStartRequest;
import com.smartbus.dto.response.ReroutingHistoryDto;
import com.smartbus.dto.response.TripResponse;
import com.smartbus.entity.*;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private final java.util.concurrent.ConcurrentHashMap<Long, List<ReroutingHistoryDto>> rerouteHistoryMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> activeTripAlternativeRouteMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public ReroutingHistoryDto reportRoadIssue(Long tripId, ReportRoadIssueRequest request) {
        Trip trip = findOrThrow(tripId);
        Long recordId = System.currentTimeMillis();

        ReroutingHistoryDto history = ReroutingHistoryDto.builder()
                .id(recordId)
                .tripId(tripId)
                .busNumber(trip.getBus().getBusNumber())
                .routeName(trip.getRoute().getName())
                .issueType(request.getIssueType())
                .reason(request.getDescription() != null ? request.getDescription() : "Driver reported " + request.getIssueType())
                .status("PROPOSED")
                .currentLatitude(request.getCurrentLatitude())
                .currentLongitude(request.getCurrentLongitude())
                .reportedAt(LocalDateTime.now())
                .build();

        rerouteHistoryMap.computeIfAbsent(tripId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(0, history);

        notificationService.createSystemNotification(
                "ROAD_ISSUE_REPORTED",
                "Road Obstruction Reported",
                "Driver of bus " + trip.getBus().getBusNumber() + " reported: " + request.getIssueType() + ". Alternative route proposed.",
                trip, trip.getBus());

        log.info("Road issue reported for tripId={}: {}", tripId, request.getIssueType());
        return history;
    }

    @Transactional
    public ReroutingHistoryDto rerouteTrip(Long tripId, RerouteRequest request) {
        Trip trip = findOrThrow(tripId);

        if (request.getAlternativeGeometryGeojson() != null) {
            activeTripAlternativeRouteMap.put(tripId, request.getAlternativeGeometryGeojson());
        }

        Long recordId = System.currentTimeMillis();
        ReroutingHistoryDto approved = ReroutingHistoryDto.builder()
                .id(recordId)
                .tripId(tripId)
                .busNumber(trip.getBus().getBusNumber())
                .routeName(trip.getRoute().getName())
                .issueType("REROUTE_APPROVED")
                .reason(request.getReason() != null ? request.getReason() : "Alternative road route approved by admin")
                .status("APPROVED")
                .alternativeGeometryGeojson(request.getAlternativeGeometryGeojson())
                .reportedAt(LocalDateTime.now())
                .approvedBy(request.getApprovedBy() != null ? request.getApprovedBy() : "Admin")
                .build();

        rerouteHistoryMap.computeIfAbsent(tripId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(0, approved);

        notificationService.createSystemNotification(
                "REROUTE_APPLIED",
                "Alternative Route Applied",
                "Bus " + trip.getBus().getBusNumber() + " rerouted along approved alternative roads. ETAs updated.",
                trip, trip.getBus());

        log.info("Reroute applied for tripId={}", tripId);
        return approved;
    }

    @Transactional(readOnly = true)
    public String getActiveTripRoute(Long tripId) {
        if (activeTripAlternativeRouteMap.containsKey(tripId)) {
            return activeTripAlternativeRouteMap.get(tripId);
        }
        Trip trip = findOrThrow(tripId);
        return trip.getRoute().getGeometryGeojson();
    }

    @Transactional(readOnly = true)
    public List<ReroutingHistoryDto> getReroutingHistory(Long tripId) {
        return rerouteHistoryMap.getOrDefault(tripId, List.of());
    }

    @Transactional
    public TripResponse startTrip(TripStartRequest request) {
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));
        Driver driver = driverRepository.findById(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", request.getDriverId()));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route", request.getRouteId()));

        // Check no active trip already running for this bus
        tripRepository.findTopByBusIdAndStatusOrderByCreatedAtDesc(bus.getId(), "IN_PROGRESS")
                .ifPresent(t -> { throw new BadRequestException(
                        "Bus already has an active trip. End the current trip first."); });

        Trip trip = Trip.builder()
                .bus(bus)
                .driver(driver)
                .route(route)
                .tripType(request.getTripType())
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now())
                .notes(request.getNotes())
                .build();

        trip = tripRepository.save(trip);

        // Update bus status
        bus.setStatus("ON_ROUTE");
        busRepository.save(bus);

        // Send notification
        notificationService.createSystemNotification(
                "BUS_STARTED",
                "Trip Started",
                "Bus " + bus.getBusNumber() + " has started the " + trip.getTripType() + " trip on " + route.getName(),
                trip, bus);

        log.info("Trip started: busId={}, driverId={}", bus.getId(), driver.getId());
        return toResponse(trip);
    }

    @Transactional
    public TripResponse endTrip(Long tripId) {
        Trip trip = findOrThrow(tripId);
        if (!"IN_PROGRESS".equals(trip.getStatus())) {
            throw new BadRequestException("Trip is not in progress. Current status: " + trip.getStatus());
        }

        trip.setStatus("COMPLETED");
        trip.setEndTime(LocalDateTime.now());
        tripRepository.save(trip);

        Bus bus = trip.getBus();
        bus.setStatus("OFFLINE");
        busRepository.save(bus);

        notificationService.createSystemNotification(
                "TRIP_COMPLETED",
                "Trip Completed",
                "Bus " + bus.getBusNumber() + " has completed its trip.",
                trip, bus);

        log.info("Trip ended: tripId={}", tripId);
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getActiveTrips() {
        return tripRepository.findByStatusWithDetails("IN_PROGRESS").stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<TripResponse> getMyTrips(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        Driver driver = driverRepository.findByUserId(user.getId()).orElse(null);
        if (driver == null) {
            // If user has ADMIN or other role, return all recent trips
            if (user.getRole() == Role.ADMIN || user.getRole() == Role.SCHOOL_ADMIN) {
                return tripRepository.findAll().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        List<Trip> trips = tripRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        if (trips.isEmpty()) {
            // Auto-provision assigned trips for the driver from their assigned bus and route
            Bus bus = busRepository.findAll().stream()
                    .filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driver.getId()))
                    .findFirst()
                    .orElse(busRepository.findAll().stream().findFirst().orElse(null));

            Route route = (bus != null && bus.getRoute() != null)
                    ? bus.getRoute()
                    : routeRepository.findAll().stream().findFirst().orElse(null);

            if (bus != null && route != null) {
                Trip defaultTrip = Trip.builder()
                        .bus(bus)
                        .driver(driver)
                        .route(route)
                        .tripType("MORNING")
                        .status("SCHEDULED")
                        .startTime(LocalDateTime.now().withHour(7).withMinute(30).withSecond(0))
                        .notes("Daily morning pickup trip")
                        .build();
                defaultTrip = tripRepository.save(defaultTrip);
                trips = new java.util.ArrayList<>();
                trips.add(defaultTrip);
                log.info("Auto-provisioned default scheduled trip id={} for driver {}", defaultTrip.getId(), driver.getUser().getName());
            }
        }

        return trips.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TripResponse getTripById(Long id) {
        Trip trip = findOrThrow(id);
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getTripsByDriver(Long driverId) {
        return tripRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TripResponse startScheduledTrip(Long tripId) {
        Trip trip = findOrThrow(tripId);
        if ("IN_PROGRESS".equals(trip.getStatus())) {
            return toResponse(trip);
        }

        trip.setStatus("IN_PROGRESS");
        trip.setStartTime(LocalDateTime.now());
        trip = tripRepository.save(trip);

        Bus bus = trip.getBus();
        bus.setStatus("ON_ROUTE");
        busRepository.save(bus);

        notificationService.createSystemNotification(
                "BUS_STARTED",
                "Trip Started",
                "Bus " + bus.getBusNumber() + " has started trip #" + trip.getId() + " on " + trip.getRoute().getName(),
                trip, bus);

        log.info("Started scheduled tripId={}, busId={}", tripId, bus.getId());
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public Page<TripResponse> getAllTrips(Pageable pageable) {
        return tripRepository.findAll(pageable).map(this::toResponse);
    }

    private Trip findOrThrow(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", id));
    }

    public TripResponse toResponse(Trip t) {
        return TripResponse.builder()
                .id(t.getId())
                .busId(t.getBus().getId())
                .busNumber(t.getBus().getBusNumber())
                .busName(t.getBus().getBusName())
                .driverId(t.getDriver().getId())
                .driverName(t.getDriver().getUser().getName())
                .routeId(t.getRoute().getId())
                .routeName(t.getRoute().getName())
                .tripType(t.getTripType())
                .status(t.getStatus())
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
