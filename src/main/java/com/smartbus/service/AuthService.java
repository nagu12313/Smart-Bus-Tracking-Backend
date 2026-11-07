package com.smartbus.service;

import com.smartbus.dto.request.LoginRequest;
import com.smartbus.dto.request.RegisterRequest;
import com.smartbus.dto.request.ResetPasswordRequest;
import com.smartbus.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    void resetPassword(ResetPasswordRequest request);
}
