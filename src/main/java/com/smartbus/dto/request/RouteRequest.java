package com.smartbus.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class RouteRequest {

    @NotBlank(message = "Route name is required")
    @Size(max = 150, message = "Route name cannot exceed 150 characters")
    private String name;

    @NotBlank(message = "Start point is required")
    private String startPoint;

    @NotBlank(message = "End point is required")
    private String endPoint;

    private String distance;
    private String geometryGeojson;
    private Integer durationMinutes;
    private Boolean isActive;

    /** Optional: list of stops with full GPS details in order */
    private List<RouteStopRequest> stops;

    @Data
    public static class RouteStopRequest {
        private Long stopId;
        private String stopName;
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
        private Double lat;
        private Double lng;
        private Integer stopOrder;
        private String arrivalTime;
    }
}
