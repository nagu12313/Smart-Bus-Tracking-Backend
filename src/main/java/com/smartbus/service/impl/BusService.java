package com.smartbus.service.impl;

import com.smartbus.dto.request.BusRequest;
import com.smartbus.dto.response.BusResponse;
import com.smartbus.entity.Bus;
import com.smartbus.entity.Driver;
import com.smartbus.entity.Route;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.DuplicateResourceException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusService {

    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;

    private volatile List<BusResponse> cachedBuses = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 60_000;

    @Transactional(readOnly = true)
    public List<BusResponse> getAllBuses() {
        long now = System.currentTimeMillis();
        if (cachedBuses != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return cachedBuses;
        }
        List<BusResponse> fresh = busRepository.findAllWithDetails().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        this.cachedBuses = fresh;
        this.lastCacheTime = now;
        return fresh;
    }

    public void invalidateCache() {
        this.cachedBuses = null;
        this.lastCacheTime = 0;
    }

    @Transactional(readOnly = true)
    public Page<BusResponse> searchBuses(String query, Pageable pageable) {
        return busRepository
                .findByBusNameContainingIgnoreCaseOrBusNumberContainingIgnoreCase(
                        query, query, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BusResponse getBusById(Long id) {
        Bus bus = findBusOrThrow(id);
        return toResponse(bus);
    }

    @Transactional
    public BusResponse createBus(BusRequest request) {
        if (busRepository.existsByBusNumber(request.getBusNumber())) {
            throw new DuplicateResourceException("Bus", "busNumber", request.getBusNumber());
        }

        Bus bus = Bus.builder()
                .busNumber(request.getBusNumber())
                .busName(request.getBusName())
                .capacity(request.getCapacity())
                .status(request.getStatus() != null ? request.getStatus() : "OFFLINE")
                .gpsDeviceId(request.getGpsDeviceId())
                .build();

        setDriverAndRoute(bus, request);
        Bus saved = busRepository.save(bus);
        invalidateCache();
        log.info("Bus created: {}", saved.getBusNumber());
        return toResponse(saved);
    }

    @Transactional
    public BusResponse updateBus(Long id, BusRequest request) {
        Bus bus = findBusOrThrow(id);

        // Allow updating bus number only if it changed
        if (!bus.getBusNumber().equals(request.getBusNumber())
                && busRepository.existsByBusNumber(request.getBusNumber())) {
            throw new DuplicateResourceException("Bus", "busNumber", request.getBusNumber());
        }

        bus.setBusNumber(request.getBusNumber());
        bus.setBusName(request.getBusName());
        bus.setCapacity(request.getCapacity());
        if (request.getStatus() != null) bus.setStatus(request.getStatus());
        if (request.getGpsDeviceId() != null) bus.setGpsDeviceId(request.getGpsDeviceId());

        setDriverAndRoute(bus, request);
        Bus saved = busRepository.save(bus);
        invalidateCache();
        log.info("Bus updated: {}", saved.getBusNumber());
        return toResponse(saved);
    }

    @Transactional
    public void deleteBus(Long id) {
        Bus bus = findBusOrThrow(id);
        busRepository.delete(bus);
        invalidateCache();
        log.info("Bus deleted: id={}", id);
    }

    private void setDriverAndRoute(Bus bus, BusRequest request) {
        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", request.getDriverId()));
            bus.setDriver(driver);
        } else {
            bus.setDriver(null);
        }

        if (request.getRouteId() != null) {
            Route route = routeRepository.findById(request.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Route", request.getRouteId()));
            bus.setRoute(route);
        } else {
            bus.setRoute(null);
        }
    }

    private Bus findBusOrThrow(Long id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus", id));
    }

    public BusResponse toResponse(Bus bus) {
        return BusResponse.builder()
                .id(bus.getId())
                .busNumber(bus.getBusNumber())
                .busName(bus.getBusName())
                .capacity(bus.getCapacity())
                .status(bus.getStatus())
                .driverId(bus.getDriver() != null ? bus.getDriver().getId() : null)
                .driverName(bus.getDriver() != null ? bus.getDriver().getUser().getName() : null)
                .driverPhone(bus.getDriver() != null ? bus.getDriver().getPhone() : null)
                .routeId(bus.getRoute() != null ? bus.getRoute().getId() : null)
                .routeName(bus.getRoute() != null ? bus.getRoute().getName() : null)
                .gpsDeviceId(bus.getGpsDeviceId())
                .createdAt(bus.getCreatedAt())
                .updatedAt(bus.getUpdatedAt())
                .build();
    }
}
