package com.smartbus.config;

import com.smartbus.entity.Role;
import com.smartbus.entity.User;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Initializes and ensures demo user accounts have the correct BCrypt hashed passwords.
 * Matches the frontend demo login credentials:
 * - admin@edutrack.com   -> admin123
 * - driver@edutrack.com  -> driver123
 * - parent@edutrack.com  -> parent123
 * - student@edutrack.com -> student123
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Map<String, String[]> demoAccounts = Map.of(
            "admin@edutrack.com",   new String[]{"Administrator", "admin123", "ADMIN"},
            "driver@edutrack.com",  new String[]{"Ramesh Kumar", "driver123", "DRIVER"},
            "parent@edutrack.com",  new String[]{"Priya Sharma", "parent123", "PARENT"},
            "student@edutrack.com", new String[]{"Ananya Sharma", "student123", "STUDENT"}
        );

        demoAccounts.forEach((email, details) -> {
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                // Ensure correct encoded password
                user.setPassword(passwordEncoder.encode(details[1]));
                user.setIsActive(true);
                userRepository.save(user);
                log.info("Verified demo account password for: {}", email);
            }, () -> {
                User newUser = User.builder()
                        .name(details[0])
                        .email(email)
                        .password(passwordEncoder.encode(details[1]))
                        .role(Role.valueOf(details[2]))
                        .isActive(true)
                        .build();
                userRepository.save(newUser);
                log.info("Created demo account: {}", email);
            });
        });
    }
}
