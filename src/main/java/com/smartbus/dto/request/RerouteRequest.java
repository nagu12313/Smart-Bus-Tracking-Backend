package com.smartbus.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RerouteRequest {
    private String alternativeGeometryGeojson;
    private String reason;
    private String notes;
    private String approvedBy;
}
