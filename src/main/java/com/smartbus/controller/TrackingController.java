package com.smartbus.controller;

import com.smartbus.dto.request.LocationUpdateRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.service.impl.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GPS Tracking Controller
 *
 * HOW GPS HARDWARE SENDS DATA:
 * - Method: POST /api/tracking/location
 * - Headers: X-Device-Token: <device_token>  (configured per bus in DB)
 * - Body: { busId, latitude, longitude, speed, heading, gpsTime }
 * - No JWT needed — authenticated via device token
 *
 * HOW FRONTEND READS LIVE DATA:
 * - GET /api/tracking/bus/{busId}/location  → latest position
 * - GET /api/tracking/all/latest            → all buses' latest positions
 * - GET /api/tracking/bus/{busId}/history   → historical trail
 */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Tag(name = "GPS Tracking", description = "Real-time bus location tracking")
public class TrackingController {

    private final TrackingService trackingService;

    /**
     * GPS device endpoint — no JWT, authenticated via X-Device-Token header.
     */
    @PostMapping("/location")
    @Operation(summary = "GPS device: submit location update",
               description = "Called by GPS hardware. Use X-Device-Token header for authentication.")
    public ResponseEntity<ApiResponse<LocationResponse>> recordLocation(
            @Valid @RequestBody LocationUpdateRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken) {
        LocationResponse response = trackingService.recordLocation(request, deviceToken);
        return ResponseEntity.ok(ApiResponse.success("Location recorded", response));
    }

    /**
     * Get latest GPS location for a specific bus.
     */
    @GetMapping("/bus/{busId}/location")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get latest location of a bus")
    public ResponseEntity<ApiResponse<LocationResponse>> getLatestLocation(
            @PathVariable Long busId) {
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getLatestLocation(busId)));
    }

    /**
     * Get GPS history for a bus (last N records).
     */
    @GetMapping("/bus/{busId}/history")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get location history for a bus")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getLocationHistory(
            @PathVariable Long busId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getLocationHistory(busId, limit)));
    }

    /**
     * Get latest location of ALL buses — used for fleet map view.
     */
    @GetMapping("/all/latest")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get latest location of all buses")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getAllLatestLocations() {
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getAllLatestLocations()));
    }

    /**
     * Get all active bus locations (status != OFFLINE).
     */
    @GetMapping("/active-buses")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get all active bus locations")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getActiveBusesLocations() {
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getActiveBusesLocations()));
    }
}
