package com.smartbus.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TripStartRequest {

    @NotNull(message = "Bus ID is required")
    private Long busId;

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @NotNull(message = "Route ID is required")
    private Long routeId;

    private String tripType = "MORNING"; // MORNING or EVENING
    private String notes;
}
