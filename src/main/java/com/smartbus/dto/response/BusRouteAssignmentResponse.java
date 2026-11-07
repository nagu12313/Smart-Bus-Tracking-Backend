package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusRouteAssignmentResponse {
    private Long id;
    private Long busId;
    private String busNumber;
    private String busName;
    private Long routeId;
    private String routeName;
    private Long driverId;
    private String driverName;
    private String tripType;
    private String startTime;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
