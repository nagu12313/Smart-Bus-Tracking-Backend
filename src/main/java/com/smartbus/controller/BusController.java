package com.smartbus.controller;

import com.smartbus.dto.request.BusRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.BusResponse;
import com.smartbus.dto.response.StudentResponse;
import com.smartbus.service.impl.BusService;
import com.smartbus.service.impl.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buses")
@RequiredArgsConstructor
@Tag(name = "Bus Management", description = "CRUD operations for school/college buses")
@SecurityRequirement(name = "bearerAuth")
public class BusController {

    private final BusService busService;
    private final StudentService studentService;

    @GetMapping
    @Operation(summary = "Get all buses")
    public ResponseEntity<ApiResponse<List<BusResponse>>> getAllBuses() {
        return ResponseEntity.ok(ApiResponse.success(busService.getAllBuses()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bus by ID")
    public ResponseEntity<ApiResponse<BusResponse>> getBusById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(busService.getBusById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new bus")
    public ResponseEntity<ApiResponse<BusResponse>> createBus(
            @Valid @RequestBody BusRequest request) {
        BusResponse response = busService.createBus(request);
        return ResponseEntity.status(201).body(ApiResponse.success("Bus created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a bus")
    public ResponseEntity<ApiResponse<BusResponse>> updateBus(
            @PathVariable Long id, @Valid @RequestBody BusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bus updated successfully",
                busService.updateBus(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a bus")
    public ResponseEntity<ApiResponse<Void>> deleteBus(@PathVariable Long id) {
        busService.deleteBus(id);
        return ResponseEntity.ok(ApiResponse.success("Bus deleted successfully", null));
    }

    @GetMapping("/{id}/students")
    @Operation(summary = "Get all students assigned to a bus")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudentsByBus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(studentService.getStudentsByBus(id)));
    }
}
