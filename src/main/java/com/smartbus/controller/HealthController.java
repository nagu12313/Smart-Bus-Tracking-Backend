package com.smartbus.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public Root & Health Check Controller
 * Ensures Render cloud deployment health probes and direct browser visits succeed
 * without 403 Access Denied or Whitelabel errors.
 */
@RestController
@Tag(name = "Health & Status", description = "Root health check and cloud uptime monitoring")
public class HealthController {

    @GetMapping(value = {"/", "/health", "/api/health"})
    @Operation(summary = "Cloud Health Check & Welcome Endpoint")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "Smart School & College Bus Tracking Backend");
        response.put("version", "1.0.0");
        response.put("timestamp", Instant.now().toString());
        response.put("swaggerDocs", "/swagger-ui.html");
        response.put("message", "Service is running normally.");
        return ResponseEntity.ok(response);
    }
}
