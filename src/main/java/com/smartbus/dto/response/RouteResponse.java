package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteResponse {
    private Long id;
    private String name;
    private String startPoint;
    private String endPoint;
    private String distance;
    private String geometryGeojson;
    private Integer durationMinutes;
    private Integer version;
    private Boolean isActive;
    private List<RouteStopResponse> stops;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RouteStopResponse {
        private Long stopId;
        private String stopName;
        private String address;
        private Double latitude;
        private Double longitude;
        private Integer stopOrder;
        private String arrivalTime;
    }
}
