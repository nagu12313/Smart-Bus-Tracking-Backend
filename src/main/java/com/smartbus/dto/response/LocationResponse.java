package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LocationResponse {
    private Long busId;
    private String busNumber;
    private String busName;
    private String status;

    // Display coordinates (road-snapped if isRoadMatched is true, otherwise raw GPS)
    private Double latitude;
    private Double longitude;

    // Raw unaltered GPS fix from device
    private Double rawLatitude;
    private Double rawLongitude;

    // Map-matched road coordinates (null if no reliable road match)
    private Double snappedLatitude;
    private Double snappedLongitude;
    private Boolean isRoadMatched;
    private String roadName;

    // Telemetry
    private Double speed;
    private Double heading;
    private Double accuracy; // GPS accuracy radius in meters
    private String accuracyDescription; // "High (±5m)", "Good (±15m)", "Approximate (±60m)"
    private String provider; // "gps", "network", "fused"

    // Verified Reverse-Geocoded Location
    private String locationName; // Verified place / street / area (e.g. "Sri Vasthav Men's PG, Ayyappa Society")
    private String nearbyLandmark; // Separate landmark with distance (e.g. "Near Medicover Hospital (240m)")

    // Timestamps
    private LocalDateTime gpsTime;
    private LocalDateTime receivedAt;

    // Context
    private Long tripId;
    private Long driverId;
    private String driverName;
    private String driverPhone;

    /** true if GPS data is older than staleness threshold */
    private Boolean isStale;
}
