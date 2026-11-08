package com.smartbus.service.impl;

import com.smartbus.entity.*;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.StudentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransportChatSecurityService {

    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final BusRepository busRepository;

    @Data
    @Builder
    public static class UserTransportContext {
        private Long userId;
        private String userName;
        private String email;
        private Role role;
        private boolean isAdmin;
        private List<Bus> authorizedBuses;
        private List<Student> authorizedStudents;
        private Driver driverProfile;
    }

    @Transactional(readOnly = true)
    public UserTransportContext getAuthorizedContext(User user) {
        if (user == null) {
            return UserTransportContext.builder()
                    .authorizedBuses(Collections.emptyList())
                    .authorizedStudents(Collections.emptyList())
                    .build();
        }

        Role role = user.getRole();
        boolean isAdmin = role == Role.ADMIN || role == Role.SCHOOL_ADMIN;

        final List<Bus> buses = new ArrayList<>();
        final List<Student> students = new ArrayList<>();
        Driver driverProfile = null;

        if (isAdmin) {
            buses.addAll(busRepository.findAll());
            students.addAll(studentRepository.findAll());
        } else if (role == Role.STUDENT) {
            Optional<Student> studentOpt = studentRepository.findByUserId(user.getId());
            if (studentOpt.isPresent()) {
                Student s = studentOpt.get();
                students.add(s);
                if (s.getBus() != null) {
                    buses.add(s.getBus());
                }
            }
        } else if (role == Role.PARENT) {
            // Find students by matching parent name or family name
            List<Student> allStudents = studentRepository.findAll();
            for (Student s : allStudents) {
                boolean matchName = s.getParentName() != null && user.getName() != null &&
                        s.getParentName().trim().equalsIgnoreCase(user.getName().trim());
                if (matchName) {
                    students.add(s);
                    if (s.getBus() != null && !buses.contains(s.getBus())) {
                        buses.add(s.getBus());
                    }
                }
            }
            // If none matched, check if any student has same family name
            if (students.isEmpty() && user.getName() != null) {
                String[] parts = user.getName().split(" ");
                if (parts.length > 1) {
                    String lastName = parts[parts.length - 1];
                    for (Student s : allStudents) {
                        if (s.getUser() != null && s.getUser().getName() != null &&
                                s.getUser().getName().endsWith(lastName)) {
                            students.add(s);
                            if (s.getBus() != null && !buses.contains(s.getBus())) {
                                buses.add(s.getBus());
                            }
                        }
                    }
                }
            }
            // Fallback for parent demo account if no explicit student is linked yet
            if (buses.isEmpty()) {
                if (!allStudents.isEmpty()) {
                    Student firstS = allStudents.get(0);
                    students.add(firstS);
                    if (firstS.getBus() != null && !buses.contains(firstS.getBus())) {
                        buses.add(firstS.getBus());
                    }
                }
                if (buses.isEmpty()) {
                    busRepository.findAll().stream().findFirst().ifPresent(buses::add);
                }
            }
        } else if (role == Role.DRIVER) {
            Optional<Driver> driverOpt = driverRepository.findByUserId(user.getId());
            if (driverOpt.isPresent()) {
                driverProfile = driverOpt.get();
                List<Bus> assigned = busRepository.findByDriverId(driverProfile.getId());
                buses.addAll(assigned);
            }
            if (buses.isEmpty()) {
                busRepository.findAll().stream().findFirst().ifPresent(buses::add);
            }
        }

        if (role == Role.STUDENT && buses.isEmpty()) {
            studentRepository.findAll().stream().findFirst().ifPresent(s -> {
                if (s.getBus() != null && !buses.contains(s.getBus())) {
                    buses.add(s.getBus());
                }
            });
            if (buses.isEmpty()) {
                busRepository.findAll().stream().findFirst().ifPresent(buses::add);
            }
        }

        return UserTransportContext.builder()
                .userId(user.getId())
                .userName(user.getName())
                .email(user.getEmail())
                .role(role)
                .isAdmin(isAdmin)
                .authorizedBuses(buses)
                .authorizedStudents(students)
                .driverProfile(driverProfile)
                .build();
    }
}
