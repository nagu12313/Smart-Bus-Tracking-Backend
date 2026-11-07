package com.smartbus.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceResponse {
    private Long id;
    private Long studentId;
    private String studentName;
    private String rollNumber;
    private Long tripId;
    private String tripType;
    private String type;
    private LocalDateTime recordedAt;
    private String notes;
}
