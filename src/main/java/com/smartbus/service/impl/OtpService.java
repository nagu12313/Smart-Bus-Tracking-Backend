package com.smartbus.service.impl;

import com.smartbus.exception.BadRequestException;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    private record OtpEntry(String otp, long expiresAt, boolean verified) {}

    // In-memory OTP storage with 10-minute validity
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private static final long OTP_VALIDITY_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Generate 6-digit OTP and send to recipient's email address
     */
    public void generateAndSendOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("An account with email " + normalizedEmail + " already exists. Please sign in instead.");
        }

        // Generate 6-digit OTP
        int code = 100000 + random.nextInt(900000);
        String otp = String.valueOf(code);

        long expiresAt = System.currentTimeMillis() + OTP_VALIDITY_MS;
        otpCache.put(normalizedEmail, new OtpEntry(otp, expiresAt, false));

        log.info("Generated OTP for {}: code [{}] - sending verification email asynchronously via SMTPS", normalizedEmail, otp);
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendOtpEmail(normalizedEmail, otp);
                log.info("OTP verification email successfully delivered to SMTP for {}", normalizedEmail);
            } catch (Exception e) {
                log.error("Failed to send OTP email to {}: {}", normalizedEmail, e.getMessage(), e);
            }
        });
    }

    /**
     * Verify the OTP entered by user
     */
    public boolean verifyOtp(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();
        OtpEntry entry = otpCache.get(normalizedEmail);

        if (entry == null) {
            throw new BadRequestException("No verification code found for this email. Please request a new code.");
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            otpCache.remove(normalizedEmail);
            throw new BadRequestException("Verification code has expired. Please request a new code.");
        }

        if (!entry.otp().equals(otp.trim())) {
            throw new BadRequestException("Invalid verification code. Please check and try again.");
        }

        // Mark as verified in cache
        otpCache.put(normalizedEmail, new OtpEntry(entry.otp(), entry.expiresAt(), true));
        log.info("OTP verified successfully for {}", normalizedEmail);
        return true;
    }

    /**
     * Validate that email was verified before allowing registration
     */
    public void validateRegistrationOtp(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();
        OtpEntry entry = otpCache.get(normalizedEmail);

        if (entry == null) {
            throw new BadRequestException("Email verification is required before signing up. Please request a verification code.");
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            otpCache.remove(normalizedEmail);
            throw new BadRequestException("Verification code expired. Please request a new code.");
        }

        if (!entry.otp().equals(otp.trim())) {
            throw new BadRequestException("Invalid verification code. Please enter the correct 6-digit code.");
        }

        // Remove OTP once consumed by registration
        otpCache.remove(normalizedEmail);
    }

    /**
     * Generate 6-digit OTP for password reset and email to registered user
     */
    public void generateAndSendPasswordResetOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        if (!userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("No registered account found with email: " + normalizedEmail);
        }

        int code = 100000 + random.nextInt(900000);
        String otp = String.valueOf(code);

        long expiresAt = System.currentTimeMillis() + OTP_VALIDITY_MS;
        String cacheKey = "reset:" + normalizedEmail;
        otpCache.put(cacheKey, new OtpEntry(otp, expiresAt, false));

        log.info("Generated password reset OTP for {}: code [{}] - sending email asynchronously via SMTPS", normalizedEmail, otp);
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendPasswordResetOtpEmail(normalizedEmail, otp);
                log.info("Password reset OTP email delivered to SMTP for {}", normalizedEmail);
            } catch (Exception e) {
                log.error("Failed to send password reset OTP email to {}: {}", normalizedEmail, e.getMessage(), e);
            }
        });
    }

    /**
     * Validate that password reset OTP is correct and unexpired
     */
    public void validatePasswordResetOtp(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();
        String cacheKey = "reset:" + normalizedEmail;
        OtpEntry entry = otpCache.get(cacheKey);

        if (entry == null) {
            throw new BadRequestException("No password reset code found for this email. Please request a new code.");
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            otpCache.remove(cacheKey);
            throw new BadRequestException("Password reset code has expired. Please request a new code.");
        }

        if (!entry.otp().equals(otp.trim())) {
            throw new BadRequestException("Invalid reset code. Please enter the correct 6-digit code.");
        }

        // Consume OTP
        otpCache.remove(cacheKey);
    }
}
