package com.smartbus.controller;

import com.smartbus.dto.request.RoutePreviewRequest;
import com.smartbus.dto.request.RouteRequest;
import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.RoutePreviewResponse;
import com.smartbus.dto.response.RouteResponse;
import com.smartbus.service.impl.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Route Management", description = "Manage campus bus routes and stops")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    @Operation(summary = "Get all routes")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> getAllRoutes() {
        return ResponseEntity.ok(ApiResponse.success(routeService.getAllRoutes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get route by ID with all stops")
    public ResponseEntity<ApiResponse<RouteResponse>> getRouteById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(routeService.getRouteById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new route")
    public ResponseEntity<ApiResponse<RouteResponse>> createRoute(
            @Valid @RequestBody RouteRequest request) {
        RouteResponse response = routeService.createRoute(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Route created successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a route")
    public ResponseEntity<ApiResponse<RouteResponse>> updateRoute(
            @PathVariable Long id, @Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route updated successfully",
                routeService.updateRoute(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a route")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return ResponseEntity.ok(ApiResponse.success("Route deleted successfully", null));
    }

    @GetMapping("/{id}/geometry")
    @Operation(summary = "Get route geometry GeoJSON")
    public ResponseEntity<ApiResponse<String>> getRouteGeometry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(routeService.getRouteGeometry(id)));
    }

    @PostMapping("/preview")
    @Operation(summary = "Calculate road route preview distance and duration")
    public ResponseEntity<ApiResponse<RoutePreviewResponse>> previewRoute(
            @Valid @RequestBody RoutePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(routeService.previewRoute(request)));
    }

    @PostMapping("/optimize")
    @Operation(summary = "Optimize intermediate stop order to minimize road travel distance")
    public ResponseEntity<ApiResponse<RoutePreviewResponse>> optimizeRoute(
            @Valid @RequestBody RoutePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(routeService.optimizeRoute(request)));
    }
}
