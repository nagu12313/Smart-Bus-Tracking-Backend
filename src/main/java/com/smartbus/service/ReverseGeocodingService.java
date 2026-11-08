package com.smartbus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReverseGeocodingService — High-accuracy reverse geocoding with strict landmark separation.
 *
 * Prevents false place identification (e.g. claiming a device at Sri Vasthav PG is "At Hospital").
 * Separates verified street/building from nearby prominent landmarks with distance indicators.
 */
@Service
@Slf4j
public class ReverseGeocodingService {

    @Value("${app.mapbox.token:}")
    private String mapboxToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Spatial cache keyed by ~15m grid coordinates
    private final Map<String, GeocodeResult> spatialCache = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeocodeResult {
        private String locationName;     // e.g. "Sri Vasthav Men's PG, Ayyappa Society" or "Jai Hind Gandhi Rd, Madhapur"
        private String nearbyLandmark;    // e.g. "Near Medicover Hospital (180m)" - only if within 250m but NOT the actual building
        private String roadName;          // e.g. "Jai Hind Gandhi Road"
        private String area;              // e.g. "Ayyappa Society, Madhapur"
    }

    public ReverseGeocodingService(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(4))
                .build();
        this.objectMapper = objectMapper;
    }

    public GeocodeResult reverseGeocode(double latitude, double longitude, double accuracy) {
        String cacheKey = String.format("%.4f,%.4f", latitude, longitude);
        GeocodeResult cached = spatialCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        GeocodeResult result = null;

        // 1. Primary: Nominatim OpenStreetMap (Superior for exact addresses, buildings, road names, and suburbs)
        try {
            result = reverseGeocodeOsm(latitude, longitude, accuracy);
        } catch (Exception e) {
            log.debug("OSM reverse geocoding failed: {}", e.getMessage());
        }

        // 2. Fallback: Mapbox Geocoding v5 if OSM fails or returned blank
        if (result == null || (result.getLocationName() == null && result.getRoadName() == null)) {
            if (mapboxToken != null && !mapboxToken.isBlank()) {
                try {
                    result = reverseGeocodeMapbox(latitude, longitude, accuracy);
                } catch (Exception e) {
                    log.debug("Mapbox reverse geocoding failed: {}", e.getMessage());
                }
            }
        }

        // 3. Fallback: Formatted coordinate string if external geocoding is unavailable
        if (result == null || result.getLocationName() == null || result.getLocationName().isBlank()) {
            result = GeocodeResult.builder()
                    .locationName(String.format("%.5f° N, %.5f° E", latitude, longitude))
                    .roadName("Campus Route")
                    .area("Campus Fleet Corridor")
                    .build();
        }

        // Cache result (capped to avoid memory bloat)
        if (spatialCache.size() > 5000) {
            spatialCache.clear();
        }
        spatialCache.put(cacheKey, result);

        return result;
    }

    private GeocodeResult reverseGeocodeOsm(double lat, double lon, double accuracy) {
        String url = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/reverse")
                .queryParam("format", "jsonv2")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("zoom", "18")
                .queryParam("addressdetails", "1")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "SmartCampusBusTracking/2.0 (ReverseGeocodingService)");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode addressNode = root.path("address");
            if (addressNode.isMissingNode()) {
                return null;
            }

            String road = firstNonBlank(addressNode, "road", "street", "pedestrian", "path", "highway");
            String building = firstNonBlank(addressNode, "amenity", "building", "hotel", "hostel", "shop", "tourism", "office");
            String locality = firstNonBlank(addressNode, "suburb", "neighbourhood", "residential", "quarter", "subdistrict");
            String city = firstNonBlank(addressNode, "city", "town", "district", "county");

            // Calculate distance to feature coordinate if present
            double featLat = root.path("lat").asDouble(lat);
            double featLon = root.path("lon").asDouble(lon);
            double distToFeatureMeters = GpsValidationService.calculateDistanceMeters(lat, lon, featLat, featLon);

            String verifiedLocationName;
            String nearbyLandmark = null;

            // Strict Landmark Separation:
            // Only attribute a building/POI as current location if within 30 meters AND accuracy is good (<= 35m).
            // If distance > 30 meters, the building is classified as a nearby landmark (e.g. "Near XYZ Hospital (180m)").
            if (building != null && !building.isBlank() && distToFeatureMeters <= 30.0 && accuracy <= 35.0) {
                // Confirmed at the building!
                verifiedLocationName = building;
                if (road != null && !road.isBlank()) {
                    verifiedLocationName += ", " + road;
                }
                if (locality != null && !locality.isBlank()) {
                    verifiedLocationName += ", " + locality;
                }
            } else if (building != null && !building.isBlank() && distToFeatureMeters <= 200.0) {
                // Device is near the landmark, but NOT at it!
                nearbyLandmark = String.format("Near %s (%dm away)", building, Math.round(distToFeatureMeters));
                verifiedLocationName = buildStreetAreaName(road, locality, city);
            } else {
                verifiedLocationName = buildStreetAreaName(road, locality, city);
            }

            return GeocodeResult.builder()
                    .locationName(verifiedLocationName)
                    .nearbyLandmark(nearbyLandmark)
                    .roadName(road != null ? road : "Local Road")
                    .area(locality != null ? locality : (city != null ? city : "Transit Zone"))
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing OSM response: {}", e.getMessage());
            return null;
        }
    }

    private GeocodeResult reverseGeocodeMapbox(double lat, double lon, double accuracy) {
        String url = UriComponentsBuilder.fromHttpUrl(
                String.format("https://api.mapbox.com/geocoding/v5/mapbox.places/%f,%f.json", lon, lat))
                .queryParam("access_token", mapboxToken)
                .queryParam("country", "in")
                .queryParam("types", "poi,address,neighborhood,locality")
                .queryParam("limit", "5")
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode features = root.path("features");
            if (!features.isArray() || features.size() == 0) {
                return null;
            }

            String road = null;
            String locality = null;
            String building = null;
            double buildingDist = Double.MAX_VALUE;

            for (JsonNode f : features) {
                String placeType = f.path("place_type").path(0).asText("");
                String text = f.path("text").asText("");
                JsonNode center = f.path("center");

                double featLon = center.path(0).asDouble(lon);
                double featLat = center.path(1).asDouble(lat);
                double d = GpsValidationService.calculateDistanceMeters(lat, lon, featLat, featLon);

                if ("poi".equals(placeType) && building == null) {
                    building = text;
                    buildingDist = d;
                } else if ("address".equals(placeType) && road == null) {
                    road = text;
                } else if (("neighborhood".equals(placeType) || "locality".equals(placeType)) && locality == null) {
                    locality = text;
                }
            }

            String verifiedLocationName;
            String nearbyLandmark = null;

            if (building != null && buildingDist <= 30.0 && accuracy <= 35.0) {
                verifiedLocationName = building;
                if (road != null) verifiedLocationName += ", " + road;
                if (locality != null) verifiedLocationName += ", " + locality;
            } else if (building != null && buildingDist <= 200.0) {
                nearbyLandmark = String.format("Near %s (%dm away)", building, Math.round(buildingDist));
                verifiedLocationName = buildStreetAreaName(road, locality, null);
            } else {
                verifiedLocationName = buildStreetAreaName(road, locality, null);
            }

            return GeocodeResult.builder()
                    .locationName(verifiedLocationName)
                    .nearbyLandmark(nearbyLandmark)
                    .roadName(road != null ? road : "Roadway")
                    .area(locality != null ? locality : "Transit Zone")
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing Mapbox response: {}", e.getMessage());
            return null;
        }
    }

    private String firstNonBlank(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
        }
        return null;
    }

    private String buildStreetAreaName(String road, String locality, String city) {
        StringBuilder sb = new StringBuilder();
        if (road != null && !road.isBlank()) {
            sb.append(road);
        }
        if (locality != null && !locality.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(locality);
        }
        if (city != null && !city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        return sb.length() > 0 ? sb.toString() : "Campus Road Network";
    }
}
