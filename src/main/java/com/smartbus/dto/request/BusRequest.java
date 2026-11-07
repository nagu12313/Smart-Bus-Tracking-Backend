package com.smartbus.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BusRequest {

    @NotBlank(message = "Bus number is required")
    @Size(max = 20, message = "Bus number cannot exceed 20 characters")
    private String busNumber;

    @NotBlank(message = "Bus name is required")
    @Size(max = 100, message = "Bus name cannot exceed 100 characters")
    private String busName;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 100, message = "Capacity cannot exceed 100")
    private Integer capacity;

    private String status;
    private Long driverId;
    private Long routeId;
    private String gpsDeviceId;
}
