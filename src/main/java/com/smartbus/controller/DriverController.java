package com.smartbus.controller;

import com.smartbus.dto.request.DriverRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.DriverResponse;
import com.smartbus.service.impl.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Management", description = "CRUD operations for drivers")
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    @Operation(summary = "Get all drivers")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getAllDrivers() {
        return ResponseEntity.ok(ApiResponse.success(driverService.getAllDrivers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by ID")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriverById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(driverService.getDriverById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new driver")
    public ResponseEntity<ApiResponse<DriverResponse>> createDriver(
            @Valid @RequestBody DriverRequest request) {
        DriverResponse response = driverService.createDriver(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Driver created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a driver")
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Driver updated successfully",
                driverService.updateDriver(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a driver")
    public ResponseEntity<ApiResponse<Void>> deleteDriver(@PathVariable Long id) {
        driverService.deleteDriver(id);
        return ResponseEntity.ok(ApiResponse.success("Driver deleted successfully", null));
    }
}
