package com.smartbus.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StudentRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    private String password;

    @NotBlank(message = "Roll number is required")
    private String rollNumber;

    @NotBlank(message = "Class name is required")
    private String className;

    private String section;
    private String parentName;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian phone number")
    private String parentPhone;

    private Long busId;
    private Long pickupStopId;
    private Long dropStopId;
}
