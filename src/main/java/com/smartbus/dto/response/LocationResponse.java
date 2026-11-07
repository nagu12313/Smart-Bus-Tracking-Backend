package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LocationResponse {
    private Long busId;
    private String busNumber;
    private String busName;
    private String status;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double heading;
    private LocalDateTime gpsTime;
    private LocalDateTime receivedAt;
    private Long tripId;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    /** true if GPS data is older than 5 minutes */
    private Boolean isStale;
}
