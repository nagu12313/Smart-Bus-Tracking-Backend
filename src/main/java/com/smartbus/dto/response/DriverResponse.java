package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DriverResponse {
    private Long id;
    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String licenseNumber;
    private String address;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
