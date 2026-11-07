package com.smartbus.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DriverRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    private String password;

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian phone number")
    private String phone;

    private String address;
    private String status;
}
