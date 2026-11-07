package com.smartbus.service.impl;

import com.smartbus.dto.request.StudentRequest;
import com.smartbus.dto.response.StudentResponse;
import com.smartbus.entity.*;
import com.smartbus.exception.DuplicateResourceException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final BusRepository busRepository;
    private final StopRepository stopRepository;
    private final PasswordEncoder passwordEncoder;

    private volatile List<StudentResponse> cachedStudents = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 60_000;

    @Transactional(readOnly = true)
    public List<StudentResponse> getAllStudents() {
        long now = System.currentTimeMillis();
        if (cachedStudents != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return cachedStudents;
        }
        List<StudentResponse> fresh = studentRepository.findAllWithDetails().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        this.cachedStudents = fresh;
        this.lastCacheTime = now;
        return fresh;
    }

    public void invalidateCache() {
        this.cachedStudents = null;
        this.lastCacheTime = 0;
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> searchStudents(String query, Pageable pageable) {
        return studentRepository
                .findByUserNameContainingIgnoreCaseOrRollNumberContainingIgnoreCase(
                        query, query, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudentById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudentByUserId(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        return toResponse(student);
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> getStudentsByBus(Long busId) {
        return studentRepository.findByBusId(busId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public StudentResponse createStudent(StudentRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (studentRepository.existsByRollNumber(request.getRollNumber())) {
            throw new DuplicateResourceException("Student", "rollNumber", request.getRollNumber());
        }

        String rawPassword = request.getPassword() != null ? request.getPassword() : "student123";
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.STUDENT)
                .isActive(true)
                .build();
        userRepository.save(user);

        Student student = Student.builder()
                .user(user)
                .rollNumber(request.getRollNumber())
                .className(request.getClassName())
                .section(request.getSection())
                .parentName(request.getParentName())
                .parentPhone(request.getParentPhone())
                .build();

        setAssignments(student, request);
        Student saved = studentRepository.save(student);
        invalidateCache();
        log.info("Student created: {} ({})", user.getEmail(), saved.getRollNumber());
        return toResponse(saved);
    }

    @Transactional
    public StudentResponse updateStudent(Long id, StudentRequest request) {
        Student student = findOrThrow(id);
        User user = student.getUser();

        if (!student.getRollNumber().equals(request.getRollNumber())
                && studentRepository.existsByRollNumber(request.getRollNumber())) {
            throw new DuplicateResourceException("Student", "rollNumber", request.getRollNumber());
        }

        user.setName(request.getName());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userRepository.save(user);

        student.setRollNumber(request.getRollNumber());
        student.setClassName(request.getClassName());
        student.setSection(request.getSection());
        student.setParentName(request.getParentName());
        student.setParentPhone(request.getParentPhone());

        setAssignments(student, request);
        invalidateCache();
        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = findOrThrow(id);
        User user = student.getUser();
        studentRepository.delete(student);
        if (user != null) {
            userRepository.delete(user);
        }
        invalidateCache();
        log.info("Student and associated user deleted: id={}", id);
    }

    private void setAssignments(Student student, StudentRequest request) {
        if (request.getBusId() != null) {
            student.setBus(busRepository.findById(request.getBusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId())));
        } else {
            student.setBus(null);
        }
        if (request.getPickupStopId() != null) {
            student.setPickupStop(stopRepository.findById(request.getPickupStopId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stop", request.getPickupStopId())));
        } else {
            student.setPickupStop(null);
        }
        if (request.getDropStopId() != null) {
            student.setDropStop(stopRepository.findById(request.getDropStopId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stop", request.getDropStopId())));
        } else {
            student.setDropStop(null);
        }
    }

    private Student findOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    public StudentResponse toResponse(Student s) {
        return StudentResponse.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .name(s.getUser().getName())
                .email(s.getUser().getEmail())
                .rollNumber(s.getRollNumber())
                .className(s.getClassName())
                .section(s.getSection())
                .parentName(s.getParentName())
                .parentPhone(s.getParentPhone())
                .busId(s.getBus() != null ? s.getBus().getId() : null)
                .busNumber(s.getBus() != null ? s.getBus().getBusNumber() : null)
                .pickupStopId(s.getPickupStop() != null ? s.getPickupStop().getId() : null)
                .pickupStopName(s.getPickupStop() != null ? s.getPickupStop().getName() : null)
                .dropStopId(s.getDropStop() != null ? s.getDropStop().getId() : null)
                .dropStopName(s.getDropStop() != null ? s.getDropStop().getName() : null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
