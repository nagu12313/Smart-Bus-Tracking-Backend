package com.smartbus.controller;

import com.smartbus.dto.request.BusRouteAssignmentRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.BusRouteAssignmentResponse;
import com.smartbus.service.BusRouteAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Tag(name = "Bus Route Assignments", description = "Assign buses to routes, drivers, and scheduled trips")
@SecurityRequirement(name = "bearerAuth")
public class BusRouteAssignmentController {

    private final BusRouteAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Assign a bus to a route and driver")
    public ResponseEntity<ApiResponse<BusRouteAssignmentResponse>> createAssignment(
            @Valid @RequestBody BusRouteAssignmentRequest request) {
        BusRouteAssignmentResponse response = assignmentService.createAssignment(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Bus route assignment created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all bus route assignments")
    public ResponseEntity<ApiResponse<List<BusRouteAssignmentResponse>>> getAllAssignments() {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getAllAssignments()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get assignment by ID")
    public ResponseEntity<ApiResponse<BusRouteAssignmentResponse>> getAssignmentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getAssignmentById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Operation(summary = "Update a bus route assignment")
    public ResponseEntity<ApiResponse<BusRouteAssignmentResponse>> updateAssignment(
            @PathVariable Long id, @Valid @RequestBody BusRouteAssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Assignment updated",
                assignmentService.updateAssignment(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an assignment")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Assignment deleted", null));
    }
}
