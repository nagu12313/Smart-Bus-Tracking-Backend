package com.smartbus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.entity.Bus;
import com.smartbus.entity.Route;
import com.smartbus.entity.RouteStop;
import com.smartbus.repository.RouteRepository;
import com.smartbus.repository.RouteStopRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RoadMapMatchingService — Enterprise road alignment and snapping.
 * Ensures the bus visually travels strictly along real roads and assigned routes,
 * while preserving actual GPS coordinates separately as required by GIS audit standards.
 */
@Service
@Slf4j
public class RoadMapMatchingService {

    private static final double MAX_CORRIDOR_SNAP_DISTANCE_METERS = 40.0;

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapMatchResult {
        private boolean matched;
        private Double snappedLatitude;
        private Double snappedLongitude;
        private String roadName;
        private double distanceToRoadMeters;
        private Double roadHeading; // Bearing of the road segment
    }

    public RoadMapMatchingService(RouteRepository routeRepository,
                                  RouteStopRepository routeStopRepository,
                                  RestTemplateBuilder builder,
                                  ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Map-matches a raw GPS reading to the nearest road network.
     *
     * @param bus Current bus entity (may have assigned route)
     * @param rawLat Raw GPS latitude
     * @param rawLng Raw GPS longitude
     * @param accuracy GPS accuracy in meters
     * @return MapMatchResult containing snapped coordinates if reliable, or original coords if unreliably far.
     */
    public MapMatchResult matchToRoad(Bus bus, double rawLat, double rawLng, double accuracy) {
        // If GPS accuracy is very poor (> 60m), do NOT snap to arbitrary parallel roads
        if (accuracy > 60.0) {
            return MapMatchResult.builder()
                    .matched(false)
                    .distanceToRoadMeters(0.0)
                    .build();
        }

        // 1. Primary Strategy: Check against Bus's Assigned Route Geometry
        if (bus != null && bus.getRoute() != null && bus.getRoute().getId() != null) {
            try {
                MapMatchResult routeMatch = matchAgainstAssignedRoute(bus.getRoute().getId(), rawLat, rawLng);
                if (routeMatch != null && routeMatch.isMatched()) {
                    return routeMatch;
                }
            } catch (Exception e) {
                log.debug("Route geometry match failed: {}", e.getMessage());
            }
        }

        // 2. Secondary Strategy: Public Road Network via OSRM Nearest API
        try {
            MapMatchResult osrmMatch = matchAgainstOsrmNearest(rawLat, rawLng);
            if (osrmMatch != null && osrmMatch.isMatched()) {
                return osrmMatch;
            }
        } catch (Exception e) {
            log.debug("OSRM nearest road match failed: {}", e.getMessage());
        }

        // No reliable road within corridor threshold
        return MapMatchResult.builder()
                .matched(false)
                .distanceToRoadMeters(0.0)
                .build();
    }

    MapMatchResult matchAgainstAssignedRoute(Long routeId, double lat, double lng) {
        Route route = routeRepository.findById(routeId).orElse(null);
        if (route == null) return null;

        List<double[]> points = new ArrayList<>();

        // A. Try parsing GeoJSON LineString geometry
        String geojson = route.getGeometryGeojson();
        if (geojson != null && !geojson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(geojson);
                JsonNode coordinates = root.path("coordinates");
                if (coordinates.isArray() && coordinates.size() >= 2) {
                    for (JsonNode c : coordinates) {
                        if (c.isArray() && c.size() >= 2) {
                            points.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()}); // [lat, lng]
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // B. Fallback to route stops sequence
        if (points.size() < 2) {
            List<RouteStop> stops = routeStopRepository.findByRouteIdWithStop(routeId);
            for (RouteStop rs : stops) {
                if (rs.getStop() != null && rs.getStop().getLatitude() != null && rs.getStop().getLongitude() != null) {
                    points.add(new double[]{rs.getStop().getLatitude(), rs.getStop().getLongitude()});
                }
            }
        }

        if (points.size() < 2) return null;

        // Find closest perpendicular projection across all road segments
        double minDistance = Double.MAX_VALUE;
        double[] bestSnapped = null;
        Double bestHeading = null;

        for (int i = 0; i < points.size() - 1; i++) {
            double[] a = points.get(i);
            double[] b = points.get(i + 1);

            double[] projected = projectPointOnSegment(lat, lng, a[0], a[1], b[0], b[1]);
            double dist = GpsValidationService.calculateDistanceMeters(lat, lng, projected[0], projected[1]);

            if (dist < minDistance) {
                minDistance = dist;
                bestSnapped = projected;
                bestHeading = BusHeadingService.calculateBearing(a[0], a[1], b[0], b[1]);
            }
        }

        if (bestSnapped != null && minDistance <= MAX_CORRIDOR_SNAP_DISTANCE_METERS) {
            return MapMatchResult.builder()
                    .matched(true)
                    .snappedLatitude(bestSnapped[0])
                    .snappedLongitude(bestSnapped[1])
                    .roadName(route.getName())
                    .distanceToRoadMeters(minDistance)
                    .roadHeading(bestHeading)
                    .build();
        }

        return null;
    }

    private MapMatchResult matchAgainstOsrmNearest(double lat, double lon) {
        String url = String.format("http://router.project-osrm.org/nearest/v1/driving/%f,%f?number=1", lon, lat);
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode waypoints = root.path("waypoints");
            if (!waypoints.isArray() || waypoints.size() == 0) {
                return null;
            }

            JsonNode wp = waypoints.get(0);
            double distMeters = wp.path("distance").asDouble(Double.MAX_VALUE);
            JsonNode loc = wp.path("location");
            double matchedLon = loc.path(0).asDouble();
            double matchedLat = loc.path(1).asDouble();
            String name = wp.path("name").asText("Road Network");

            if (distMeters <= MAX_CORRIDOR_SNAP_DISTANCE_METERS) {
                return MapMatchResult.builder()
                        .matched(true)
                        .snappedLatitude(matchedLat)
                        .snappedLongitude(matchedLon)
                        .roadName(name.isBlank() ? "Public Road" : name)
                        .distanceToRoadMeters(distMeters)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Error parsing OSRM nearest response: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Projects point P(pLat, pLng) onto line segment from A(aLat, aLng) to B(bLat, bLng).
     */
    public static double[] projectPointOnSegment(double pLat, double pLng, double aLat, double aLng, double bLat, double bLng) {
        double dx = bLng - aLng;
        double dy = bLat - aLat;

        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) {
            return new double[]{aLat, aLng};
        }

        // Projection scalar t on vector AB
        double t = ((pLng - aLng) * dx + (pLat - aLat) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t)); // clamp to segment endpoints

        double snappedLng = aLng + t * dx;
        double snappedLat = aLat + t * dy;
        return new double[]{snappedLat, snappedLng};
    }
}
