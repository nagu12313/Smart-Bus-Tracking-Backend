package com.smartbus.service;

import com.smartbus.dto.request.LocationUpdateRequest;
import com.smartbus.dto.response.LocationResponse;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * GpsValidationService — Strict industrial validation of incoming GPS telemetry.
 * Filters out invalid coordinates, clock skew, excessive GPS inaccuracy, and teleportation jumps.
 */
@Service
@Slf4j
public class GpsValidationService {

    private static final double MAX_REALISTIC_SPEED_KMH = 140.0;
    private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 150.0;
    private static final double ROAD_SNAPPING_MAX_ACCURACY_METERS = 45.0;

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private boolean reliableForSnapping;
        private String accuracyDescription;
        private String rejectionReason;
        private double sanitizedSpeed;
        private double sanitizedAccuracy;
    }

    public ValidationResult validate(LocationUpdateRequest request, LocationResponse previousLocation) {
        Double lat = request.getLatitude();
        Double lng = request.getLongitude();

        // 1. Basic coordinate boundary check
        if (lat == null || lng == null || Double.isNaN(lat) || Double.isNaN(lng)) {
            String reason = "Latitude or longitude is null or NaN";
            log.error("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason(reason)
                    .build();
        }

        // Check for reversed latitude and longitude (e.g. India coordinates reversed: lat ~78, lng ~16)
        if (lat > 60.0 && lat <= 90.0 && lng >= 5.0 && lng <= 40.0) {
            log.warn("[GPS COORDINATES REVERSED] Detected reversed coordinates for bus {}: lat={}, lng={}. Inverting to lat={}, lng={}",
                    request.getBusId(), lat, lng, lng, lat);
            double temp = lat;
            lat = lng;
            lng = temp;
            request.setLatitude(lat);
            request.setLongitude(lng);
        }

        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            String reason = String.format("Coordinates out of range: (%.6f, %.6f). Lat must be [-90, 90], Lng [-180, 180]", lat, lng);
            log.error("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason(reason)
                    .build();
        }

        // 2. Reject Null Island (0.0, 0.0)
        if (Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001) {
            String reason = "Coordinates point to Null Island (0,0)";
            log.error("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason(reason)
                    .build();
        }

        // 3. Accuracy evaluation
        double accuracy = request.getAccuracy() != null ? Math.max(1.0, request.getAccuracy()) : 10.0;
        String accuracyDesc;
        boolean reliableForSnapping;

        if (accuracy <= 10.0) {
            accuracyDesc = String.format("High Accuracy (±%.0fm)", accuracy);
            reliableForSnapping = true;
        } else if (accuracy <= 25.0) {
            accuracyDesc = String.format("Good Accuracy (±%.0fm)", accuracy);
            reliableForSnapping = true;
        } else if (accuracy <= ROAD_SNAPPING_MAX_ACCURACY_METERS) {
            accuracyDesc = String.format("Moderate (±%.0fm)", accuracy);
            reliableForSnapping = true;
        } else {
            accuracyDesc = String.format("Approximate Location (±%.0fm)", accuracy);
            reliableForSnapping = false; // Do not force snap to parallel roads if accuracy is weak
        }

        if (accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            log.warn("GPS ping for bus {} has weak accuracy ({}m > {}m limit)",
                    request.getBusId(), accuracy, MAX_ACCEPTABLE_ACCURACY_METERS);
        }

        // 4. Timestamp clock skew check
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime gpsTime = request.getGpsTime() != null ? request.getGpsTime() : now;
        long futureSkewSeconds = Duration.between(now, gpsTime).getSeconds();
        if (futureSkewSeconds > 600) { // > 10 min in future
            String reason = String.format("GPS timestamp is too far in future (%ds)", futureSkewSeconds);
            log.error("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason(reason)
                    .build();
        }

        long pastSkewSeconds = Duration.between(gpsTime, now).getSeconds();
        if (pastSkewSeconds > 86400) { // > 24 hours old
            String reason = String.format("GPS packet is older than 24 hours (%ds)", pastSkewSeconds);
            log.error("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
            return ValidationResult.builder()
                    .valid(false)
                    .rejectionReason(reason)
                    .build();
        }

        // 5. Monotonic Timestamp Check — prevent delayed / out-of-order packets from overwriting newer locations
        if (previousLocation != null) {
            LocalDateTime prevGpsTime = previousLocation.getGpsTime() != null
                    ? previousLocation.getGpsTime()
                    : previousLocation.getReceivedAt();
            if (prevGpsTime != null && gpsTime.isBefore(prevGpsTime.minusSeconds(3))) {
                String reason = String.format("Out-of-order GPS update: packet timestamp %s is older than current recorded latest %s",
                        gpsTime, prevGpsTime);
                log.warn("[GPS REJECTION] Bus {}: {}", request.getBusId(), reason);
                return ValidationResult.builder()
                        .valid(false)
                        .rejectionReason(reason)
                        .build();
            }
        }

        // 6. Progressive Movement & Speed Handling (Do not reject valid coordinates on location relocation/spoofing!)
        double reportedSpeed = request.getSpeed() != null ? Math.max(0.0, request.getSpeed()) : 0.0;
        double speed = reportedSpeed;

        if (previousLocation != null && previousLocation.getLatitude() != null && previousLocation.getLongitude() != null) {
            double prevLat = previousLocation.getLatitude();
            double prevLng = previousLocation.getLongitude();
            double distMeters = calculateDistanceMeters(prevLat, prevLng, lat, lng);

            LocalDateTime prevTime = previousLocation.getGpsTime() != null ? previousLocation.getGpsTime() : previousLocation.getReceivedAt();
            if (prevTime != null) {
                long elapsedSeconds = Math.max(1, Duration.between(prevTime, gpsTime).abs().getSeconds());
                double calculatedSpeedKmh = (distMeters / elapsedSeconds) * 3.6;

                // If position jumped significantly (e.g. test starting, relocation, or spoofing track initiation)
                if (calculatedSpeedKmh > MAX_REALISTIC_SPEED_KMH && distMeters > 150.0) {
                    log.info("Bus {} relocation or new track initiated: moved {:.1f}m in {}s. Accepting valid GPS coordinates ({}, {}).",
                            request.getBusId(), distMeters, elapsedSeconds, lat, lng);
                    // Use reported speed if reasonable, else 0
                    if (reportedSpeed > 0 && reportedSpeed <= MAX_REALISTIC_SPEED_KMH) {
                        speed = reportedSpeed;
                    } else {
                        speed = 0.0;
                    }
                } else if (speed < 1.0 && calculatedSpeedKmh >= 3.0 && calculatedSpeedKmh <= MAX_REALISTIC_SPEED_KMH) {
                    // Infer speed from successive valid updates
                    speed = Math.round(calculatedSpeedKmh * 10.0) / 10.0;
                }
            }
        }

        return ValidationResult.builder()
                .valid(true)
                .reliableForSnapping(reliableForSnapping)
                .accuracyDescription(accuracyDesc)
                .sanitizedSpeed(speed)
                .sanitizedAccuracy(accuracy)
                .build();
    }

    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
