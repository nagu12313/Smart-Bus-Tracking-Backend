package com.smartbus.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * BusHeadingService — Dynamic heading and vehicle orientation determination.
 * Ensures the yellow school bus always points in its real travel direction,
 * preserving orientation when stopped and eliminating jitter/spinning.
 */
@Service
@Slf4j
public class BusHeadingService {

    private static final double MIN_SPEED_FOR_HEADING_KMH = 2.0;
    private static final double MIN_MOVE_METERS_FOR_BEARING = 5.0;

    /**
     * Resolves the authoritative heading (0–360°) for a vehicle.
     *
     * @param reportedHeading Device GPS compass heading (nullable)
     * @param currentSpeed Speed in km/h
     * @param currentLat Current latitude
     * @param currentLng Current longitude
     * @param previousLat Previous latitude (nullable)
     * @param previousLng Previous longitude (nullable)
     * @param previousHeading Previous known heading (nullable)
     * @param roadHeading Heading from road geometry if snapped (nullable)
     * @return Heading in degrees (0–360°), North = 0°, East = 90°
     */
    public Double resolveHeading(Double reportedHeading,
                                 double currentSpeed,
                                 double currentLat,
                                 double currentLng,
                                 Double previousLat,
                                 Double previousLng,
                                 Double previousHeading,
                                 Double roadHeading) {

        // 1. Stationary Vehicle Rule:
        // When vehicle is stopped or moving < 2 km/h, NEVER rotate randomly! Hold last known heading.
        if (currentSpeed < MIN_SPEED_FOR_HEADING_KMH) {
            if (previousHeading != null && !previousHeading.isNaN()) {
                return normalizeDegrees(previousHeading);
            }
            if (roadHeading != null && !roadHeading.isNaN()) {
                return normalizeDegrees(roadHeading);
            }
            return 90.0; // Default East
        }

        // 2. Device GPS Heading:
        // If reported by hardware GNSS and speed is sufficient, trust device heading
        if (reportedHeading != null && !reportedHeading.isNaN() && reportedHeading >= 0.0 && reportedHeading <= 360.0) {
            return normalizeDegrees(reportedHeading);
        }

        // 3. Coordinate Forward Azimuth:
        // Calculate bearing from consecutive GPS points if moved > 5 meters
        if (previousLat != null && previousLng != null) {
            double distanceMoved = GpsValidationService.calculateDistanceMeters(previousLat, previousLng, currentLat, currentLng);
            if (distanceMoved >= MIN_MOVE_METERS_FOR_BEARING) {
                double computed = calculateBearing(previousLat, previousLng, currentLat, currentLng);
                return normalizeDegrees(computed);
            }
        }

        // 4. Road Segment Heading:
        if (roadHeading != null && !roadHeading.isNaN()) {
            return normalizeDegrees(roadHeading);
        }

        // 5. Fallback: Hold previous heading
        if (previousHeading != null && !previousHeading.isNaN()) {
            return normalizeDegrees(previousHeading);
        }

        return 90.0;
    }

    /**
     * Calculates forward azimuth (bearing) from point 1 to point 2 using spherical trigonometry.
     * Returns 0–360°, where 0° = North, 90° = East, 180° = South, 270° = West.
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δλ = Math.toRadians(lon2 - lon1);

        double y = Math.sin(Δλ) * Math.cos(φ2);
        double x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);

        double θ = Math.atan2(y, x);
        return (Math.toDegrees(θ) + 360.0) % 360.0;
    }

    public static double normalizeDegrees(double degrees) {
        return ((degrees % 360.0) + 360.0) % 360.0;
    }
}
