package com.smartbus.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRoadIssueRequest {
    @NotBlank(message = "Issue type is required")
    private String issueType; // HEAVY_TRAFFIC, ROAD_BLOCKED, CONSTRUCTION, ACCIDENT, VEHICLE_ISSUE, OTHER

    private String description;
    private Double currentLatitude;
    private Double currentLongitude;
}
