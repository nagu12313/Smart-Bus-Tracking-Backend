package com.smartbus.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GpsDeviceRegisterRequest {

    @NotBlank(message = "Device identifier is required")
    private String deviceIdentifier;

    @NotBlank(message = "Device name is required")
    private String deviceName;

    private String deviceToken;
    private Long busId;
    private String firmwareVersion;
    private String simCardNumber;
}
