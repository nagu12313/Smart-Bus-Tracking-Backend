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
}
