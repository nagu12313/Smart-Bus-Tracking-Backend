package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudentResponse {
    private Long id;
    private Long userId;
    private String name;
    private String email;
    private String rollNumber;
    private String className;
    private String section;
    private String parentName;
    private String parentPhone;
    private Long busId;
    private String busNumber;
    private Long pickupStopId;
    private String pickupStopName;
    private Long dropStopId;
    private String dropStopName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
