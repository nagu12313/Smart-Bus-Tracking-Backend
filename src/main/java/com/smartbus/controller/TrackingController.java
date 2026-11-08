package com.smartbus.controller;

import com.smartbus.dto.request.LocationUpdateRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.LocationResponse;
import com.smartbus.entity.Bus;
import com.smartbus.entity.Role;
import com.smartbus.entity.User;
import com.smartbus.repository.UserRepository;
import com.smartbus.service.impl.TrackingService;
import com.smartbus.service.impl.TransportChatSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GPS Tracking Controller
 *
 * HOW GPS HARDWARE SENDS DATA:
 * - Method: POST /api/tracking/location
 * - Headers: X-Device-Token: <device_token>  (configured per bus in DB)
 * - Body: { busId, latitude, longitude, speed, heading, gpsTime }
 * - No JWT needed — authenticated via device token
 *
 * HOW FRONTEND READS LIVE DATA (Synchronized Single Source of Truth):
 * - GET /api/tracking/bus/{busId}/location  → latest position (Role-authorized)
 * - GET /api/tracking/all/latest            → fleet latest positions (Role-authorized)
 * - GET /api/tracking/bus/{busId}/history   → historical trail (Role-authorized)
 */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GPS Tracking", description = "Real-time bus location tracking")
public class TrackingController {

    private final TrackingService trackingService;
    private final UserRepository userRepository;
    private final TransportChatSecurityService securityService;

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
     * Get latest GPS location for a specific bus — strictly role authorized.
     */
    @GetMapping("/bus/{busId}/location")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get latest location of a bus")
    public ResponseEntity<ApiResponse<LocationResponse>> getLatestLocation(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (user != null) {
                validateBusAccess(user, busId);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getLatestLocation(busId)));
    }

    /**
     * Get GPS history for a bus (last N records) — strictly role authorized.
     */
    @GetMapping("/bus/{busId}/history")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get location history for a bus")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getLocationHistory(
            @PathVariable Long busId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (user != null) {
                validateBusAccess(user, busId);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getLocationHistory(busId, limit)));
    }

    /**
     * Get latest location of ALL buses — role-scoped for authorized fleet view.
     */
    @GetMapping("/all/latest")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get latest location of all buses")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getAllLatestLocations(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (user != null && user.getRole() != Role.ADMIN && user.getRole() != Role.SCHOOL_ADMIN) {
                TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
                Set<Long> allowedBusIds = ctx.getAuthorizedBuses().stream()
                        .map(Bus::getId)
                        .collect(Collectors.toSet());
                List<LocationResponse> filtered = trackingService.getAllLatestLocations().stream()
                        .filter(l -> allowedBusIds.contains(l.getBusId()))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(ApiResponse.success(filtered));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getAllLatestLocations()));
    }

    /**
     * Get all active bus locations (status != OFFLINE).
     */
    @GetMapping("/active-buses")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get all active bus locations")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getActiveBusesLocations(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (user != null && user.getRole() != Role.ADMIN && user.getRole() != Role.SCHOOL_ADMIN) {
                TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
                Set<Long> allowedBusIds = ctx.getAuthorizedBuses().stream()
                        .map(Bus::getId)
                        .collect(Collectors.toSet());
                List<LocationResponse> filtered = trackingService.getActiveBusesLocations().stream()
                        .filter(l -> allowedBusIds.contains(l.getBusId()))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(ApiResponse.success(filtered));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                trackingService.getActiveBusesLocations()));
    }

    /**
     * Role-based access enforcement: ensures users only query buses they are authorized to track.
     */
    private void validateBusAccess(User user, Long busId) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SCHOOL_ADMIN) {
            return; // Admins have fleet-wide visibility
        }
        TransportChatSecurityService.UserTransportContext ctx = securityService.getAuthorizedContext(user);
        boolean isAuthorized = ctx.getAuthorizedBuses().stream()
                .anyMatch(b -> b.getId().equals(busId));
        if (!isAuthorized) {
            log.warn("Access denied: User {} ({}) attempted unauthorized access to bus {}",
                    user.getEmail(), user.getRole(), busId);
            throw new AccessDeniedException("You are not authorized to view location for bus: " + busId);
        }
    }
}
