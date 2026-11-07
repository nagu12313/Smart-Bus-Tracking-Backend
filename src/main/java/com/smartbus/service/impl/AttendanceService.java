package com.smartbus.service.impl;

import com.smartbus.dto.request.AttendanceRequest;
import com.smartbus.dto.response.AttendanceResponse;
import com.smartbus.entity.Attendance;
import com.smartbus.entity.Student;
import com.smartbus.entity.Trip;
import com.smartbus.entity.User;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.DuplicateResourceException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.AttendanceRepository;
import com.smartbus.repository.StudentRepository;
import com.smartbus.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;

    @Transactional
    public AttendanceResponse recordAttendance(AttendanceRequest request, User recordedByUser) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.getStudentId()));
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip", request.getTripId()));

        // Validate type
        if (!"PICKUP".equals(request.getType()) && !"DROPOFF".equals(request.getType())) {
            throw new BadRequestException("Attendance type must be PICKUP or DROPOFF");
        }

        // Prevent duplicates
        if (attendanceRepository.existsByStudentIdAndTripIdAndType(
                request.getStudentId(), request.getTripId(), request.getType())) {
            throw new DuplicateResourceException(
                    "Attendance already recorded for this student, trip, and type");
        }

        Attendance attendance = Attendance.builder()
                .student(student)
                .trip(trip)
                .type(request.getType())
                .recordedBy(recordedByUser)
                .notes(request.getNotes())
                .build();

        attendance = attendanceRepository.save(attendance);

        // Create notification for parent/student
        String notifType = "PICKUP".equals(request.getType()) ? "PICKED_UP" : "DROPPED_OFF";
        String notifTitle = "PICKUP".equals(request.getType())
                ? student.getUser().getName() + " picked up"
                : student.getUser().getName() + " dropped off";
        String notifMsg = "PICKUP".equals(request.getType())
                ? student.getUser().getName() + " has boarded Bus " + trip.getBus().getBusNumber()
                : student.getUser().getName() + " has been dropped off.";

        notificationService.createNotificationForUser(
                student.getUser(), notifType, notifTitle, notifMsg, trip, trip.getBus());

        // Notify parent if parent_phone stored
        if (student.getParentName() != null) {
            log.info("TODO: Send SMS to parent {} for student {}", student.getParentPhone(), student.getUser().getName());
        }

        log.info("Attendance recorded: studentId={}, tripId={}, type={}", 
                student.getId(), trip.getId(), request.getType());
        return toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAllAttendance(Pageable pageable) {
        return attendanceRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAttendanceByStudent(Long studentId, Pageable pageable) {
        return attendanceRepository.findByStudentId(studentId, pageable).map(this::toResponse);
    }

    private AttendanceResponse toResponse(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId())
                .studentId(a.getStudent().getId())
                .studentName(a.getStudent().getUser().getName())
                .rollNumber(a.getStudent().getRollNumber())
                .tripId(a.getTrip().getId())
                .tripType(a.getTrip().getTripType())
                .type(a.getType())
                .recordedAt(a.getRecordedAt())
                .notes(a.getNotes())
                .build();
    }
}
