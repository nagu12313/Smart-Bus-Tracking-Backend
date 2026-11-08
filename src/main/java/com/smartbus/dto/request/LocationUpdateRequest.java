package com.smartbus.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPS Location Update Request.
 * Sent by physical GPS hardware or mobile driver app.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {

    @NotNull(message = "Bus ID is required")
    private Long busId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0",  message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
    private Double longitude;

    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    @DecimalMax(value = "200.0", message = "Speed cannot exceed 200 km/h")
    private Double speed = 0.0;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "360.0")
    private Double heading;

    @DecimalMin(value = "0.0", message = "Accuracy cannot be negative")
    private Double accuracy = 5.0;

    private String provider = "gps";
    private Double altitude = 0.0;

    public void setGpsAccuracy(Double gpsAccuracy) {
        this.accuracy = gpsAccuracy;
    }

    public Double getGpsAccuracy() {
        return this.accuracy;
    }

    /** GPS device timestamp (ISO-8601). Defaults to server time if null. */
    private LocalDateTime gpsTime;

    private Long tripId;
}
