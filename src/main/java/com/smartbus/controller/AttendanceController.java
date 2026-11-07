package com.smartbus.controller;

import com.smartbus.dto.request.AttendanceRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.AttendanceResponse;
import com.smartbus.entity.User;
import com.smartbus.repository.UserRepository;
import com.smartbus.service.impl.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Student pickup and drop-off attendance")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Record student pickup or drop-off")
    public ResponseEntity<ApiResponse<AttendanceResponse>> recordAttendance(
            @Valid @RequestBody AttendanceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        AttendanceResponse response = attendanceService.recordAttendance(request, currentUser);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Attendance recorded successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all attendance records (paginated)")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getAllAttendance(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                attendanceService.getAllAttendance(pageable)));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get attendance records for a specific student")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getStudentAttendance(
            @PathVariable Long studentId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                attendanceService.getAttendanceByStudent(studentId, pageable)));
    }
}
