package com.smartbus.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusRouteAssignmentRequest {
    @NotNull(message = "Bus ID is required")
    private Long busId;

    @NotNull(message = "Route ID is required")
    private Long routeId;

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @Builder.Default
    private String tripType = "MORNING";

    @Builder.Default
    private String startTime = "07:30 AM";

    @Builder.Default
    private Boolean isActive = true;
}
