package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TripResponse {
    private Long id;
    private Long busId;
    private String busNumber;
    private String busName;
    private Long driverId;
    private String driverName;
    private Long routeId;
    private String routeName;
    private String tripType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String notes;
    private LocalDateTime createdAt;
}
