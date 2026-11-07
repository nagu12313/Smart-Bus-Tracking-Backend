package com.smartbus.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.dto.response.LocationSearchResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class LocationSearchService {

    @org.springframework.beans.factory.annotation.Value("${app.mapbox.token:}")
    private String mapboxToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LocationSearchService(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(6))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Search any real-world location across India using Mapbox Places Geocoding API,
     * with Nominatim OpenStreetMap as fallback.
     */
    public List<LocationSearchResultDto> searchLocations(String query) {
        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        // 1. Primary: Mapbox Places API with user's access token
        try {
            String mapboxUrl = UriComponentsBuilder.fromHttpUrl(
                    "https://api.mapbox.com/geocoding/v5/mapbox.places/" + UriComponentsBuilder.fromPath(query.trim()).toUriString() + ".json"
            )
            .queryParam("access_token", mapboxToken)
            .queryParam("country", "in")
            .queryParam("limit", "10")
            .queryParam("language", "en")
            .toUriString();

            ResponseEntity<String> response = restTemplate.getForEntity(mapboxUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode features = root.path("features");
                if (features.isArray() && features.size() > 0) {
                    List<LocationSearchResultDto> results = new ArrayList<>();
                    for (JsonNode f : features) {
                        String placeName = f.path("text").asText(f.path("place_name").asText("Stop Location"));
                        String displayName = f.path("place_name").asText(placeName);
                        JsonNode center = f.path("center"); // [lon, lat]
                        double lon = center.get(0).asDouble();
                        double lat = center.get(1).asDouble();

                        String type = "place";
                        JsonNode placeTypes = f.path("place_type");
                        if (placeTypes.isArray() && placeTypes.size() > 0) {
                            type = placeTypes.get(0).asText("place");
                        }

                        results.add(LocationSearchResultDto.builder()
                                .name(placeName)
                                .displayName(displayName)
                                .latitude(lat)
                                .longitude(lon)
                                .type(type)
                                .city("")
                                .state("India")
                                .postcode("")
                                .build());
                    }
                    return results;
                }
            }
        } catch (Exception e) {
            log.warn("Mapbox Places search error for '{}': {}. Falling back to OSM.", query, e.getMessage());
        }

        // 2. Fallback: Nominatim OpenStreetMap
        try {
            String osmUrl = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("format", "json")
                    .queryParam("q", query.trim())
                    .queryParam("countrycodes", "in")
                    .queryParam("limit", "10")
                    .queryParam("addressdetails", "1")
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SmartSchoolBusTracker/2.0 (Campus Fleet Road Tracking)");
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(osmUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                List<LocationSearchResultDto> results = new ArrayList<>();
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        String displayName = node.path("display_name").asText("");
                        String placeName = displayName.split(",")[0].trim();
                        double lat = node.path("lat").asDouble();
                        double lon = node.path("lon").asDouble();
                        String type = node.path("type").asText("place");

                        results.add(LocationSearchResultDto.builder()
                                .name(placeName)
                                .displayName(displayName)
                                .latitude(lat)
                                .longitude(lon)
                                .type(type)
                                .city("")
                                .state("India")
                                .postcode("")
                                .build());
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("OSM fallback search error for '{}': {}", query, e.getMessage());
        }

        return Collections.emptyList();
    }
}
