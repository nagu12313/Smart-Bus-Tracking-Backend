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

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final StopRepository stopRepository;

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

            if (stop == null || linkedStopIds.contains(stop.getId())) {
                stop = Stop.builder()
                        .name(sName)
                        .address(sAddr)
                        .latitude(sLat)
                        .longitude(sLng)
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
        if (stops == null || stops.size() < 2) {
            return RoutePreviewResponse.builder()
                    .distanceKm(0.0)
                    .durationMinutes(0)
                    .stops(stops)
                    .message("At least 2 points are required to calculate road route.")
                    .build();
        }

        double totalDist = calculateTotalDistanceKm(stops);
        int duration = Math.max(5, (int) Math.round((totalDist / 30.0) * 60.0));

        return RoutePreviewResponse.builder()
                .distanceKm(Math.round(totalDist * 100.0) / 100.0)
                .durationMinutes(duration)
                .stops(stops)
                .message("Road route preview calculated successfully")
                .build();
    }

    public RoutePreviewResponse optimizeRoute(RoutePreviewRequest request) {
        List<RouteStopPointDto> stops = request.getStops();
        if (stops == null || stops.size() < 4) {
            return previewRoute(request);
        }

        RouteStopPointDto start = stops.get(0);
        RouteStopPointDto end = stops.get(stops.size() - 1);
        List<RouteStopPointDto> intermediate = new ArrayList<>(stops.subList(1, stops.size() - 1));

        // Nearest neighbor optimization on intermediate stops
        List<RouteStopPointDto> optimized = new ArrayList<>();
        double currentLat = start.getLatitude();
        double currentLon = start.getLongitude();

        while (!intermediate.isEmpty()) {
            int nearestIdx = 0;
            double minDist = Double.MAX_VALUE;

            for (int i = 0; i < intermediate.size(); i++) {
                RouteStopPointDto candidate = intermediate.get(i);
                double d = haversineKm(currentLat, currentLon, candidate.getLatitude(), candidate.getLongitude());
                if (d < minDist) {
                    minDist = d;
                    nearestIdx = i;
                }
            }

            RouteStopPointDto next = intermediate.remove(nearestIdx);
            optimized.add(next);
            currentLat = next.getLatitude();
            currentLon = next.getLongitude();
        }

        List<RouteStopPointDto> fullOptimized = new ArrayList<>();
        fullOptimized.add(start);
        fullOptimized.addAll(optimized);
        fullOptimized.add(end);

        for (int i = 0; i < fullOptimized.size(); i++) {
            fullOptimized.get(i).setStopOrder(i + 1);
        }

        double optimizedDist = calculateTotalDistanceKm(fullOptimized);
        int duration = Math.max(5, (int) Math.round((optimizedDist / 30.0) * 60.0));

        return RoutePreviewResponse.builder()
                .distanceKm(Math.round(optimizedDist * 100.0) / 100.0)
                .durationMinutes(duration)
                .stops(fullOptimized)
                .message("Intermediate stops rearranged to minimize road travel distance")
                .build();
    }

    private double calculateTotalDistanceKm(List<RouteStopPointDto> points) {
        double dist = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            RouteStopPointDto p1 = points.get(i);
            RouteStopPointDto p2 = points.get(i + 1);
            if (p1.getLatitude() != null && p1.getLongitude() != null &&
                p2.getLatitude() != null && p2.getLongitude() != null) {
                dist += haversineKm(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude()) * 1.25;
            }
        }
        return dist;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
