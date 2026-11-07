package com.smartbus.service;

import com.smartbus.dto.request.BusRouteAssignmentRequest;
import com.smartbus.dto.response.BusRouteAssignmentResponse;
import com.smartbus.entity.Bus;
import com.smartbus.entity.BusRouteAssignment;
import com.smartbus.entity.Driver;
import com.smartbus.entity.Route;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.BusRouteAssignmentRepository;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusRouteAssignmentService {

    private final BusRouteAssignmentRepository assignmentRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final DriverRepository driverRepository;

    @Transactional
    public BusRouteAssignmentResponse createAssignment(BusRouteAssignmentRequest request) {
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route", request.getRouteId()));
        Driver driver = driverRepository.findById(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", request.getDriverId()));

        if (!route.getIsActive()) {
            throw new BadRequestException("Cannot assign inactive route: " + route.getName());
        }

        BusRouteAssignment assignment = BusRouteAssignment.builder()
                .bus(bus)
                .route(route)
                .driver(driver)
                .tripType(request.getTripType() != null ? request.getTripType() : "MORNING")
                .startTime(request.getStartTime() != null ? request.getStartTime() : "07:30 AM")
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        assignment = assignmentRepository.save(assignment);

        // Keep bus in sync with assigned route and driver
        bus.setRoute(route);
        bus.setDriver(driver);
        busRepository.save(bus);

        log.info("Created assignment: Bus {} -> Route {} -> Driver {}", bus.getBusNumber(), route.getName(), driver.getUser() != null ? driver.getUser().getName() : driver.getId());
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<BusRouteAssignmentResponse> getAllAssignments() {
        return assignmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BusRouteAssignmentResponse getAssignmentById(Long id) {
        BusRouteAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusRouteAssignment", id));
        return toResponse(assignment);
    }

    @Transactional
    public BusRouteAssignmentResponse updateAssignment(Long id, BusRouteAssignmentRequest request) {
        BusRouteAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusRouteAssignment", id));

        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route", request.getRouteId()));
        Driver driver = driverRepository.findById(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", request.getDriverId()));

        assignment.setBus(bus);
        assignment.setRoute(route);
        assignment.setDriver(driver);
        if (request.getTripType() != null) assignment.setTripType(request.getTripType());
        if (request.getStartTime() != null) assignment.setStartTime(request.getStartTime());
        if (request.getIsActive() != null) assignment.setIsActive(request.getIsActive());

        assignment = assignmentRepository.save(assignment);

        bus.setRoute(route);
        bus.setDriver(driver);
        busRepository.save(bus);

        return toResponse(assignment);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        BusRouteAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusRouteAssignment", id));
        assignmentRepository.delete(assignment);
        log.info("Deleted assignment id={}", id);
    }

    private BusRouteAssignmentResponse toResponse(BusRouteAssignment bra) {
        String driverName = (bra.getDriver() != null && bra.getDriver().getUser() != null)
                ? bra.getDriver().getUser().getName()
                : "Assigned Driver";

        return BusRouteAssignmentResponse.builder()
                .id(bra.getId())
                .busId(bra.getBus() != null ? bra.getBus().getId() : null)
                .busNumber(bra.getBus() != null ? bra.getBus().getBusNumber() : "N/A")
                .busName(bra.getBus() != null ? bra.getBus().getBusName() : "N/A")
                .routeId(bra.getRoute() != null ? bra.getRoute().getId() : null)
                .routeName(bra.getRoute() != null ? bra.getRoute().getName() : "N/A")
                .driverId(bra.getDriver() != null ? bra.getDriver().getId() : null)
                .driverName(driverName)
                .tripType(bra.getTripType())
                .startTime(bra.getStartTime())
                .isActive(bra.getIsActive())
                .createdAt(bra.getCreatedAt())
                .build();
    }
}
