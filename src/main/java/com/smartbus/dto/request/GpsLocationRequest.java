package com.smartbus.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GpsLocationRequest {

    @NotBlank(message = "Device identifier is required")
    private String deviceId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    @DecimalMax(value = "200.0", message = "Speed exceeds realistic vehicle limit")
    @Builder.Default
    private Double speed = 0.0;

    @DecimalMin(value = "0.0", message = "Heading must be between 0 and 360")
    @DecimalMax(value = "360.0", message = "Heading must be between 0 and 360")
    @Builder.Default
    private Double heading = 0.0;

    @Builder.Default
    private Double accuracy = 5.0;

    @Builder.Default
    private Double altitude = 0.0;

    @Builder.Default
    private Boolean ignitionOn = true;

    private LocalDateTime timestamp;
}
