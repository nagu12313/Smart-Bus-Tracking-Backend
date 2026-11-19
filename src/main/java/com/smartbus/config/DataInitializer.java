package com.smartbus.config;

import com.smartbus.entity.Bus;
import com.smartbus.entity.Driver;
import com.smartbus.entity.Role;
import com.smartbus.entity.Route;
import com.smartbus.entity.Student;
import com.smartbus.entity.Trip;
import com.smartbus.entity.User;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.RouteRepository;
import com.smartbus.repository.StudentRepository;
import com.smartbus.repository.TripRepository;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Initializes and ensures demo user accounts have the correct BCrypt hashed passwords,
 * and sets up essential demo Driver, Student, Bus, and Trip associations.
 * Demo login credentials:
 * - admin@edutrack.com    -> admin123     (ADMIN)
 * - driver@edutrack.com   -> driver123    (DRIVER)
 * - parent@edutrack.com   -> parent123    (PARENT)
 * - student@edutrack.com  -> student123   (STUDENT)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final StudentRepository studentRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Clean up legacy mock tester account if present
        try {
            userRepository.findByEmail("mocktest@edutrack.com").ifPresent(userRepository::delete);
        } catch (Exception ignored) {}

        // Ensure Primary Admin account (bollanagu6868@gmail.com) exists and is active
        userRepository.findByEmail("bollanagu6868@gmail.com").ifPresentOrElse(admin -> {
            admin.setRole(Role.ADMIN);
            admin.setIsActive(true);
            userRepository.save(admin);
            log.info("Primary admin account active: bollanagu6868@gmail.com");
        }, () -> {
            User primaryAdmin = User.builder()
                    .name("Administrator")
                    .email("bollanagu6868@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(primaryAdmin);
            log.info("Initialized primary admin account: bollanagu6868@gmail.com");
        });

        // Deactivate all demo accounts so they cannot be used to sign in
        List<String> demoEmails = List.of(
            "admin@edutrack.com",
            "driver@edutrack.com",
            "parent@edutrack.com",
            "student@edutrack.com",
            "mocktest@edutrack.com"
        );
        demoEmails.forEach(email -> {
            userRepository.findByEmail(email).ifPresent(user -> {
                user.setIsActive(false);
                userRepository.save(user);
                log.info("Disabled demo account from sign in: {}", email);
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

                // Ensure driver has assigned scheduled trips
                List<Trip> driverTrips = tripRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
                if (driverTrips.isEmpty()) {
                    Route route = bus.getRoute() != null ? bus.getRoute() : routeRepository.findAll().stream().findFirst().orElse(null);
                    if (route != null) {
                        Trip morningTrip = Trip.builder()
                                .bus(bus)
                                .driver(driver)
                                .route(route)
                                .tripType("MORNING")
                                .status("SCHEDULED")
                                .startTime(LocalDateTime.now().withHour(7).withMinute(30).withSecond(0))
                                .notes("Morning pickup route for students and staff")
                                .build();
                        tripRepository.save(morningTrip);

                        Trip eveningTrip = Trip.builder()
                                .bus(bus)
                                .driver(driver)
                                .route(route)
                                .tripType("EVENING")
                                .status("SCHEDULED")
                                .startTime(LocalDateTime.now().withHour(16).withMinute(30).withSecond(0))
                                .notes("Evening return drop route to campus terminal")
                                .build();
                        tripRepository.save(eveningTrip);
                        log.info("Created 2 default assigned trips for driver {}", driverUser.getName());
                    }
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
