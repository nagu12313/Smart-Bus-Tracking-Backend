package com.smartbus.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePreviewRequest {
    @NotEmpty(message = "Stops list cannot be empty")
    private List<RouteStopPointDto> stops;
}
