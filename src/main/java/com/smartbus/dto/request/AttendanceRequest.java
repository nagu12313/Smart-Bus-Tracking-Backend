package com.smartbus.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttendanceRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Trip ID is required")
    private Long tripId;

    /** PICKUP or DROPOFF */
    @NotNull(message = "Attendance type is required")
    private String type;

    private String notes;
}
