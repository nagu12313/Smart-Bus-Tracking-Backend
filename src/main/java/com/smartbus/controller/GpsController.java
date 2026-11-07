package com.smartbus.controller;

import com.smartbus.dto.request.GpsDeviceRegisterRequest;
import com.smartbus.dto.request.GpsLocationRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.GpsDeviceResponse;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.service.GpsDeviceService;
import com.smartbus.service.GpsIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GpsController — Hardware Ingestion and Tracker Management APIs.
 */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Tag(name = "GPS Hardware Ingestion", description = "Physical GPS Tracker Ingestion Gateway & Device Registry")
public class GpsController {

    private final GpsIngestionService gpsIngestionService;
    private final GpsDeviceService gpsDeviceService;

    /**
     * Primary GPS hardware ingestion endpoint.
     * Authenticated strictly via X-Device-Token header and deviceId — no JWT required.
     */
    @PostMapping("/device/location")
    @Operation(summary = "Ingest physical GPS hardware location update",
               description = "Called by physical SIM/LTE trackers installed in buses. Authenticated via X-Device-Token header.")
    public ResponseEntity<ApiResponse<LocationResponse>> ingestDeviceLocation(
            @Valid @RequestBody GpsLocationRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken) {
        LocationResponse response = gpsIngestionService.ingestLocation(request, deviceToken);
        return ResponseEntity.ok(ApiResponse.success("GPS packet ingested successfully", response));
    }

    /**
     * Admin: Register a new physical GPS hardware tracker.
     */
    @PostMapping("/devices")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Register a new GPS hardware device", description = "Admin only")
    public ResponseEntity<ApiResponse<GpsDeviceResponse>> registerDevice(
            @Valid @RequestBody GpsDeviceRegisterRequest request) {
        GpsDeviceResponse response = gpsDeviceService.registerDevice(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("GPS hardware device registered", response));
    }

    /**
     * Admin: List all registered GPS hardware devices.
     */
    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all registered GPS hardware devices")
    public ResponseEntity<ApiResponse<List<GpsDeviceResponse>>> getAllDevices() {
        return ResponseEntity.ok(ApiResponse.success(gpsDeviceService.getAllDevices()));
    }

    /**
     * Retrieve status of a single hardware device.
     */
    @GetMapping("/device/{deviceId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get single GPS hardware device status")
    public ResponseEntity<ApiResponse<GpsDeviceResponse>> getDeviceStatus(
            @PathVariable String deviceId) {
        return ResponseEntity.ok(ApiResponse.success(gpsDeviceService.getDeviceByIdentifier(deviceId)));
    }
}
