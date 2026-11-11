package com.smartbus.controller;

import com.smartbus.dto.request.ReportRoadIssueRequest;
import com.smartbus.dto.request.RerouteRequest;
import com.smartbus.dto.request.TripStartRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.ReroutingHistoryDto;
import com.smartbus.dto.response.TripResponse;
import com.smartbus.service.impl.TripService;
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

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trip Management", description = "Start, end, and track trips")
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final TripService tripService;

    @GetMapping("/my-trips")
    @Operation(summary = "Get trips assigned to the currently logged in driver")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getMyTrips(
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "driver@edutrack.com";
        return ResponseEntity.ok(ApiResponse.success(tripService.getMyTrips(username)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trip by ID with route and bus details")
    public ResponseEntity<ApiResponse<TripResponse>> getTripById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getTripById(id)));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get all trips for a specific driver")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getTripsByDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getTripsByDriver(driverId)));
    }

    @PutMapping("/{id}/start")
    @Operation(summary = "Start an assigned scheduled trip directly")
    public ResponseEntity<ApiResponse<TripResponse>> startScheduledTrip(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Trip started successfully", tripService.startScheduledTrip(id)));
    }

    @PostMapping("/start")
    @Operation(summary = "Start a new trip")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(
            @Valid @RequestBody TripStartRequest request) {
        TripResponse response = tripService.startTrip(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Trip started successfully", response));
    }

    @PutMapping("/{id}/end")
    @Operation(summary = "End an active trip")
    public ResponseEntity<ApiResponse<TripResponse>> endTrip(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Trip ended successfully",
                tripService.endTrip(id)));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active (in-progress) trips")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getActiveTrips() {
        return ResponseEntity.ok(ApiResponse.success(tripService.getActiveTrips()));
    }

    @GetMapping
    @Operation(summary = "Get all trips (paginated)")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getAllTrips(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getAllTrips(pageable)));
    }

    @PostMapping("/{tripId}/report-road-issue")
    @Operation(summary = "Driver reports traffic delay, road blockage, or accident")
    public ResponseEntity<ApiResponse<ReroutingHistoryDto>> reportRoadIssue(
            @PathVariable Long tripId,
            @Valid @RequestBody ReportRoadIssueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Road obstruction reported. Alternative route proposed.",
                tripService.reportRoadIssue(tripId, request)));
    }

    @PostMapping("/{tripId}/reroute")
    @Operation(summary = "Approve and apply alternative road detour route")
    public ResponseEntity<ApiResponse<ReroutingHistoryDto>> rerouteTrip(
            @PathVariable Long tripId,
            @RequestBody RerouteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Alternative route applied successfully",
                tripService.rerouteTrip(tripId, request)));
    }

    @GetMapping("/{tripId}/route")
    @Operation(summary = "Get active trip road route geometry (including any approved detour)")
    public ResponseEntity<ApiResponse<String>> getActiveTripRoute(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getActiveTripRoute(tripId)));
    }

    @GetMapping("/{tripId}/rerouting-history")
    @Operation(summary = "Get rerouting audit history for trip")
    public ResponseEntity<ApiResponse<List<ReroutingHistoryDto>>> getReroutingHistory(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getReroutingHistory(tripId)));
    }
}
