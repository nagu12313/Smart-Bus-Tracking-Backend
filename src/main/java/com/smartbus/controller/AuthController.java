package com.smartbus.controller;

import com.smartbus.dto.request.LoginRequest;
import com.smartbus.dto.request.RegisterRequest;
import com.smartbus.dto.request.SendOtpRequest;
import com.smartbus.dto.request.VerifyOtpRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.AuthResponse;
import com.smartbus.service.AuthService;
import com.smartbus.service.impl.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, registration, and email verification endpoints")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP", description = "Send a 6-digit email verification code via Gmail SMTP")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.generateAndSendOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "A 6-digit verification code has been sent to " + request.getEmail(), request.getEmail()));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify the 6-digit email verification code")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean verified = otpService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", verified));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate and receive JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Register a new user account with verified email OTP")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Registration successful", response));
    }
}
