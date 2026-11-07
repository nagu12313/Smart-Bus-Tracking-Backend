package com.smartbus.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationSearchResultDto {
    private String name;
    private String displayName;
    private Double latitude;
    private Double longitude;
    private String type;
    private String city;
    private String state;
    private String postcode;
}
