package com.smartbus.dto.response;

import com.smartbus.dto.request.RouteStopPointDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePreviewResponse {
    private Double distanceKm;
    private Integer durationMinutes;
    private String geometryGeojson;
    private List<RouteStopPointDto> stops;
    private String message;

    // Advanced Road Optimization Metrics
    private Double originalDistanceKm;
    private Integer originalDurationMinutes;
    private Double optimizedDistanceKm;
    private Integer optimizedDurationMinutes;
    private Double distanceSavedKm;
    private Double percentageSaved;
    private Integer durationSavedMinutes;
    private Boolean isOptimized;
}
