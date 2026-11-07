package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusResponse {
    private Long id;
    private String busNumber;
    private String busName;
    private Integer capacity;
    private String status;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private Long routeId;
    private String routeName;
    private String gpsDeviceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
