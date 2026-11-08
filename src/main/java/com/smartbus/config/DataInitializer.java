package com.smartbus.config;

import com.smartbus.entity.Bus;
import com.smartbus.entity.Driver;
import com.smartbus.entity.Role;
import com.smartbus.entity.Student;
import com.smartbus.entity.User;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.StudentRepository;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Initializes and ensures demo user accounts have the correct BCrypt hashed passwords,
 * and sets up essential demo Driver, Student, and Bus associations.
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
    private final DriverRepository driverRepository;
    private final StudentRepository studentRepository;
    private final BusRepository busRepository;
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

        // Ensure Driver profile and Bus assignment
        userRepository.findByEmail("driver@edutrack.com").ifPresent(driverUser -> {
            Driver driver = driverRepository.findByUserId(driverUser.getId()).orElseGet(() -> {
                Driver d = Driver.builder()
                        .user(driverUser)
                        .licenseNumber("DL-2024-001")
                        .phone("9876500001")
                        .address("Madhapur, Hyderabad")
                        .status("ACTIVE")
                        .build();
                return driverRepository.save(d);
            });
            busRepository.findAll().stream().findFirst().ifPresent(bus -> {
                if (bus.getDriver() == null) {
                    bus.setDriver(driver);
                    busRepository.save(bus);
                    log.info("Assigned driver {} to bus {}", driverUser.getName(), bus.getBusNumber());
                }
            });
        });

        // Ensure Student profile and Bus assignment for Ananya Sharma (parent: Priya Sharma)
        userRepository.findByEmail("student@edutrack.com").ifPresent(studentUser -> {
            studentRepository.findByUserId(studentUser.getId()).ifPresentOrElse(s -> {
                if (s.getBus() == null) {
                    busRepository.findAll().stream().findFirst().ifPresent(bus -> {
                        s.setBus(bus);
                        studentRepository.save(s);
                    });
                }
            }, () -> {
                Bus firstBus = busRepository.findAll().stream().findFirst().orElse(null);
                Student newStudent = Student.builder()
                        .user(studentUser)
                        .rollNumber("CSE2401")
                        .className("B.Tech CSE")
                        .section("A")
                        .parentName("Priya Sharma")
                        .parentPhone("9876500011")
                        .bus(firstBus)
                        .build();
                studentRepository.save(newStudent);
                log.info("Created student profile for Ananya Sharma assigned to bus {}", firstBus != null ? firstBus.getBusNumber() : "none");
            });
        });
    }
}
