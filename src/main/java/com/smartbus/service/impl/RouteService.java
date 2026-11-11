package com.smartbus.service.impl;

import com.smartbus.dto.request.RoutePreviewRequest;
import com.smartbus.dto.request.RouteRequest;
import com.smartbus.dto.request.RouteStopPointDto;
import com.smartbus.dto.response.RoutePreviewResponse;
import com.smartbus.dto.response.RouteResponse;
import com.smartbus.entity.Route;
import com.smartbus.entity.RouteStop;
import com.smartbus.entity.Stop;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.RouteRepository;
import com.smartbus.repository.RouteStopRepository;
import com.smartbus.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.smartbus.service.RouteOptimizationEngine;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final StopRepository stopRepository;
    private final RouteOptimizationEngine routeOptimizationEngine;

    private volatile List<RouteResponse> cachedRoutes = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 60_000;

    @Transactional(readOnly = true)
    public List<RouteResponse> getAllRoutes() {
        long now = System.currentTimeMillis();
        if (cachedRoutes != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return cachedRoutes;
        }
        List<Route> routes = routeRepository.findAll();
        Map<Long, List<RouteStop>> stopsByRoute = routeStopRepository.findAllWithStop().stream()
                .collect(Collectors.groupingBy(rs -> rs.getRoute().getId()));

        List<RouteResponse> fresh = routes.stream()
                .map(r -> toResponse(r, stopsByRoute.getOrDefault(r.getId(), List.of())))
                .collect(Collectors.toList());
        this.cachedRoutes = fresh;
        this.lastCacheTime = now;
        return fresh;
    }

    public void invalidateCache() {
        this.cachedRoutes = null;
        this.lastCacheTime = 0;
    }

    @Transactional(readOnly = true)
    public RouteResponse getRouteById(Long id) {
        Route route = findOrThrow(id);
        List<RouteStop> stops = routeStopRepository.findByRouteIdWithStop(id);
        return toResponse(route, stops);
    }

    @Transactional
    public RouteResponse createRoute(RouteRequest request) {
        String startPoint = request.getStartPoint();
        if (startPoint != null && startPoint.length() > 150) {
            startPoint = startPoint.substring(0, 150);
        }
        String endPoint = request.getEndPoint();
        if (endPoint != null && endPoint.length() > 150) {
            endPoint = endPoint.substring(0, 150);
        }
        String distance = request.getDistance();
        if (distance != null && distance.length() > 50) {
            distance = distance.substring(0, 50);
        }

        Route route = Route.builder()
                .name(request.getName())
                .startPoint(startPoint)
                .endPoint(endPoint)
                .distance(distance)
                .geometryGeojson(request.getGeometryGeojson())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 30)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        route = routeRepository.save(route);

        if (request.getStops() != null && !request.getStops().isEmpty()) {
            saveRouteStops(route, request.getStops());
        }

        invalidateCache();
        log.info("Route created: {}", route.getName());
        return toResponse(route);
    }

    @Transactional
    public RouteResponse updateRoute(Long id, RouteRequest request) {
        Route route = findOrThrow(id);
        route.setName(request.getName());

        String startPoint = request.getStartPoint();
        if (startPoint != null && startPoint.length() > 150) {
            startPoint = startPoint.substring(0, 150);
        }
        String endPoint = request.getEndPoint();
        if (endPoint != null && endPoint.length() > 150) {
            endPoint = endPoint.substring(0, 150);
        }
        String distance = request.getDistance();
        if (distance != null && distance.length() > 50) {
            distance = distance.substring(0, 50);
        }

        route.setStartPoint(startPoint);
        route.setEndPoint(endPoint);
        if (distance != null) route.setDistance(distance);
        if (request.getGeometryGeojson() != null) route.setGeometryGeojson(request.getGeometryGeojson());
        if (request.getDurationMinutes() != null) route.setDurationMinutes(request.getDurationMinutes());
        if (request.getIsActive() != null) route.setIsActive(request.getIsActive());
        routeRepository.save(route);

        if (request.getStops() != null) {
            routeStopRepository.deleteByRouteId(id);
            routeStopRepository.flush();
            saveRouteStops(route, request.getStops());
        }

        invalidateCache();
        return toResponse(route);
    }

    @Transactional
    public void deleteRoute(Long id) {
        routeStopRepository.deleteByRouteId(id);
        routeRepository.delete(findOrThrow(id));
        invalidateCache();
        log.info("Route deleted: id={}", id);
    }

    private void saveRouteStops(Route route, List<RouteRequest.RouteStopRequest> stopRequests) {
        List<RouteStop> routeStops = new ArrayList<>();
        java.util.Set<Long> linkedStopIds = new java.util.HashSet<>();
        int order = 1;
        for (RouteRequest.RouteStopRequest sr : stopRequests) {
            Stop stop = null;
            if (sr.getStopId() != null && sr.getStopId() > 0) {
                stop = stopRepository.findById(sr.getStopId()).orElse(null);
            }

            String sName = sr.getStopName() != null && !sr.getStopName().isBlank()
                    ? sr.getStopName()
                    : (sr.getName() != null && !sr.getName().isBlank() ? sr.getName() : "Stop " + order);
            String sAddr = sr.getAddress() != null && !sr.getAddress().isBlank() ? sr.getAddress() : sName;
            Double sLat = sr.getLatitude() != null ? sr.getLatitude() : sr.getLat();
            Double sLng = sr.getLongitude() != null ? sr.getLongitude() : sr.getLng();

            if (sName.length() > 150) sName = sName.substring(0, 150);
            if (sAddr.length() > 255) sAddr = sAddr.substring(0, 255);

            if (stop != null && !linkedStopIds.contains(stop.getId())) {
                stop.setName(sName);
                stop.setAddress(sAddr);
                if (sLat != null && sLng != null) {
                    stop.setLatitude(sLat);
                    stop.setLongitude(sLng);
                }
                stop = stopRepository.save(stop);
            } else {
                stop = Stop.builder()
                        .name(sName)
                        .address(sAddr)
                        .latitude(sLat != null ? sLat : 0.0)
                        .longitude(sLng != null ? sLng : 0.0)
                        .build();
                stop = stopRepository.save(stop);
            }

            linkedStopIds.add(stop.getId());

            int stopOrder = sr.getStopOrder() != null && sr.getStopOrder() > 0 ? sr.getStopOrder() : order;
            String arrTime = sr.getArrivalTime() != null && !sr.getArrivalTime().isBlank()
                    ? sr.getArrivalTime()
                    : String.format("%02d:%02d", 8 + (order * 10) / 60, (order * 10) % 60);

            routeStops.add(RouteStop.builder()
                    .route(route)
                    .stop(stop)
                    .stopOrder(stopOrder)
                    .arrivalTime(arrTime)
                    .build());
            order++;
        }
        routeStopRepository.saveAll(routeStops);
    }

    private Route findOrThrow(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route", id));
    }

    public RouteResponse toResponse(Route route) {
        return toResponse(route, routeStopRepository.findByRouteIdWithStop(route.getId()));
    }

    public RouteResponse toResponse(Route route, List<RouteStop> routeStops) {
        List<RouteResponse.RouteStopResponse> stops = routeStops.stream()
                .map(rs -> RouteResponse.RouteStopResponse.builder()
                        .stopId(rs.getStop().getId())
                        .stopName(rs.getStop().getName())
                        .address(rs.getStop().getAddress())
                        .latitude(rs.getStop().getLatitude())
                        .longitude(rs.getStop().getLongitude())
                        .stopOrder(rs.getStopOrder())
                        .arrivalTime(rs.getArrivalTime())
                        .build())
                .collect(Collectors.toList());

        return RouteResponse.builder()
                .id(route.getId())
                .name(route.getName())
                .startPoint(route.getStartPoint())
                .endPoint(route.getEndPoint())
                .distance(route.getDistance())
                .geometryGeojson(route.getGeometryGeojson())
                .durationMinutes(route.getDurationMinutes())
                .version(route.getVersion())
                .isActive(route.getIsActive())
                .stops(stops)
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public String getRouteGeometry(Long routeId) {
        Route route = findOrThrow(routeId);
        return route.getGeometryGeojson();
    }

    public RoutePreviewResponse previewRoute(RoutePreviewRequest request) {
        List<RouteStopPointDto> stops = request.getStops();
        return routeOptimizationEngine.calculatePreview(stops, false);
    }

    public RoutePreviewResponse optimizeRoute(RoutePreviewRequest request) {
        List<RouteStopPointDto> stops = request.getStops();
        return routeOptimizationEngine.optimize(stops);
    }
}
