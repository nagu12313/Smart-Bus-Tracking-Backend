package com.smartbus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.dto.request.RouteStopPointDto;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise OSRM Road Routing and Matrix Service.
 * Retrieves real road-network distance and duration matrices (OSRM Table API)
 * and true road-following polyline geometries (OSRM Route API).
 */
@Service
@Slf4j
public class OsrmRoutingService {

    private static final String OSRM_TABLE_URL = "http://router.project-osrm.org/table/v1/driving/{coords}?annotations=distance,duration";
    private static final String OSRM_ROUTE_URL = "http://router.project-osrm.org/route/v1/driving/{coords}?overview=full&geometries=geojson&steps=true";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // In-memory cache for matrix and route results to avoid duplicate OSRM calls
    private final Map<String, RoadMatrixResult> matrixCache = new ConcurrentHashMap<>();
    private final Map<String, OsrmRouteResult> routeCache = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadMatrixResult {
        private double[][] distances; // in meters
        private double[][] durations; // in seconds
        private boolean isFromOsrm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OsrmRouteResult {
        private double distanceMeters;
        private double durationSeconds;
        private String geojson;
        private List<Double> legDistancesMeters;
        private List<Double> legDurationsSeconds;
        private boolean isFromOsrm;
    }

    public OsrmRoutingService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(6))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieve road-network distance and duration matrices between all given stops.
     */
    public RoadMatrixResult getDistanceDurationMatrix(List<RouteStopPointDto> stops) {
        int n = stops.size();
        if (n == 0) {
            return RoadMatrixResult.builder()
                    .distances(new double[0][0])
                    .durations(new double[0][0])
                    .isFromOsrm(false)
                    .build();
        }

        String cacheKey = buildCoordinateString(stops);
        if (matrixCache.containsKey(cacheKey)) {
            return matrixCache.get(cacheKey);
        }

        try {
            String url = OSRM_TABLE_URL.replace("{coords}", cacheKey);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText("");

                if ("Ok".equalsIgnoreCase(code) && root.has("distances") && root.has("durations")) {
                    JsonNode distNode = root.get("distances");
                    JsonNode durNode = root.get("durations");

                    double[][] distMatrix = new double[n][n];
                    double[][] durMatrix = new double[n][n];

                    for (int i = 0; i < n; i++) {
                        JsonNode distRow = distNode.get(i);
                        JsonNode durRow = durNode.get(i);
                        for (int j = 0; j < n; j++) {
                            if (i == j) {
                                distMatrix[i][j] = 0.0;
                                durMatrix[i][j] = 0.0;
                                continue;
                            }

                            Double d = (distRow != null && distRow.has(j) && !distRow.get(j).isNull())
                                    ? distRow.get(j).asDouble()
                                    : null;
                            Double t = (durRow != null && durRow.has(j) && !durRow.get(j).isNull())
                                    ? durRow.get(j).asDouble()
                                    : null;

                            // Handle unreachable or missing road links via Haversine road estimation
                            if (d == null || d <= 0.0) {
                                double havMeters = haversineMeters(stops.get(i), stops.get(j)) * 1.35;
                                distMatrix[i][j] = havMeters;
                                durMatrix[i][j] = (havMeters / (30.0 * 1000.0 / 3600.0)); // 30 km/h
                            } else {
                                distMatrix[i][j] = d;
                                durMatrix[i][j] = (t != null && t > 0.0) ? t : (d / (30.0 * 1000.0 / 3600.0));
                            }
                        }
                    }

                    RoadMatrixResult result = RoadMatrixResult.builder()
                            .distances(distMatrix)
                            .durations(durMatrix)
                            .isFromOsrm(true)
                            .build();

                    matrixCache.put(cacheKey, result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("OSRM Table API unavailable ({}), falling back to road-estimated Haversine matrix", e.getMessage());
        }

        // Fallback: Road-factor estimated distance and duration matrix
        RoadMatrixResult fallback = buildFallbackMatrix(stops);
        matrixCache.put(cacheKey, fallback);
        return fallback;
    }

    /**
     * Retrieve complete road-following GeoJSON polyline and leg metrics from OSRM Route API.
     */
    public OsrmRouteResult getRouteGeometry(List<RouteStopPointDto> stops) {
        int n = stops.size();
        if (n < 2) {
            return OsrmRouteResult.builder()
                    .distanceMeters(0.0)
                    .durationSeconds(0.0)
                    .geojson("{\"type\":\"LineString\",\"coordinates\":[]}")
                    .legDistancesMeters(Collections.emptyList())
                    .legDurationsSeconds(Collections.emptyList())
                    .isFromOsrm(false)
                    .build();
        }

        String cacheKey = buildCoordinateString(stops);
        if (routeCache.containsKey(cacheKey)) {
            return routeCache.get(cacheKey);
        }

        try {
            String url = OSRM_ROUTE_URL.replace("{coords}", cacheKey);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText("");

                if ("Ok".equalsIgnoreCase(code) && root.has("routes") && root.get("routes").isArray() && !root.get("routes").isEmpty()) {
                    JsonNode routeObj = root.get("routes").get(0);
                    double totalDist = routeObj.path("distance").asDouble(0.0);
                    double totalDur = routeObj.path("duration").asDouble(0.0);
                    String geojsonStr = routeObj.path("geometry").toString();

                    List<Double> legDists = new ArrayList<>();
                    List<Double> legDurs = new ArrayList<>();

                    if (routeObj.has("legs") && routeObj.get("legs").isArray()) {
                        for (JsonNode leg : routeObj.get("legs")) {
                            legDists.add(leg.path("distance").asDouble(0.0));
                            legDurs.add(leg.path("duration").asDouble(0.0));
                        }
                    }

                    OsrmRouteResult result = OsrmRouteResult.builder()
                            .distanceMeters(totalDist)
                            .durationSeconds(totalDur)
                            .geojson(geojsonStr)
                            .legDistancesMeters(legDists)
                            .legDurationsSeconds(legDurs)
                            .isFromOsrm(true)
                            .build();

                    routeCache.put(cacheKey, result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("OSRM Route API unavailable ({}), generating road-factor interpolation polyline", e.getMessage());
        }

        // Fallback: Waypoint interpolation polyline
        OsrmRouteResult fallback = buildFallbackRoute(stops);
        routeCache.put(cacheKey, fallback);
        return fallback;
    }

    private String buildCoordinateString(List<RouteStopPointDto> stops) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stops.size(); i++) {
            RouteStopPointDto s = stops.get(i);
            double lng = s.getLongitude() != null ? s.getLongitude() : 0.0;
            double lat = s.getLatitude() != null ? s.getLatitude() : 0.0;
            if (i > 0) sb.append(";");
            sb.append(String.format(Locale.US, "%.6f,%.6f", lng, lat));
        }
        return sb.toString();
    }

    private RoadMatrixResult buildFallbackMatrix(List<RouteStopPointDto> stops) {
        int n = stops.size();
        double[][] dist = new double[n][n];
        double[][] dur = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dist[i][j] = 0.0;
                    dur[i][j] = 0.0;
                } else {
                    double d = haversineMeters(stops.get(i), stops.get(j)) * 1.35; // typical road factor
                    dist[i][j] = d;
                    dur[i][j] = (d / (30.0 * 1000.0 / 3600.0)); // 30 km/h campus speed
                }
            }
        }

        return RoadMatrixResult.builder()
                .distances(dist)
                .durations(dur)
                .isFromOsrm(false)
                .build();
    }

    private OsrmRouteResult buildFallbackRoute(List<RouteStopPointDto> stops) {
        List<double[]> coords = new ArrayList<>();
        List<Double> legDists = new ArrayList<>();
        List<Double> legDurs = new ArrayList<>();
        double totalDistM = 0.0;

        for (int i = 0; i < stops.size() - 1; i++) {
            RouteStopPointDto p1 = stops.get(i);
            RouteStopPointDto p2 = stops.get(i + 1);

            double dM = haversineMeters(p1, p2) * 1.35;
            double tS = (dM / (30.0 * 1000.0 / 3600.0));
            legDists.add(dM);
            legDurs.add(tS);
            totalDistM += dM;

            int steps = Math.max(8, (int) Math.round((dM / 1000.0) * 6));
            for (int s = 0; s <= steps; s++) {
                double ratio = (double) s / steps;
                double lng = p1.getLongitude() + (p2.getLongitude() - p1.getLongitude()) * ratio;
                double lat = p1.getLatitude() + (p2.getLatitude() - p1.getLatitude()) * ratio;
                coords.add(new double[]{lng, lat});
            }
        }

        StringBuilder geojson = new StringBuilder("{\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < coords.size(); i++) {
            if (i > 0) geojson.append(",");
            geojson.append(String.format(Locale.US, "[%.6f,%.6f]", coords.get(i)[0], coords.get(i)[1]));
        }
        geojson.append("]}");

        return OsrmRouteResult.builder()
                .distanceMeters(totalDistM)
                .durationSeconds(totalDistM / (30.0 * 1000.0 / 3600.0))
                .geojson(geojson.toString())
                .legDistancesMeters(legDists)
                .legDurationsSeconds(legDurs)
                .isFromOsrm(false)
                .build();
    }

    public double haversineMeters(RouteStopPointDto p1, RouteStopPointDto p2) {
        if (p1 == null || p2 == null || p1.getLatitude() == null || p1.getLongitude() == null ||
                p2.getLatitude() == null || p2.getLongitude() == null) {
            return 0.0;
        }
        double lat1 = p1.getLatitude();
        double lon1 = p1.getLongitude();
        double lat2 = p2.getLatitude();
        double lon2 = p2.getLongitude();

        double r = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
