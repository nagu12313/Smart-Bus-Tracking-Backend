package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GpsDeviceResponse {
    private Long id;
    private String deviceIdentifier;
    private String deviceName;
    private Long busId;
    private String busNumber;
    private String deviceStatus;
    private String firmwareVersion;
    private String simCardNumber;
    private Double batteryLevel;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
}
