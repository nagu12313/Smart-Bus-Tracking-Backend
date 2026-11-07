package com.smartbus.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReroutingHistoryDto {
    private Long id;
    private Long tripId;
    private String busNumber;
    private String routeName;
    private String issueType;
    private String reason;
    private String status; // PROPOSED, APPROVED, REJECTED
    private Double currentLatitude;
    private Double currentLongitude;
    private String alternativeGeometryGeojson;
    private LocalDateTime reportedAt;
    private String approvedBy;
}
