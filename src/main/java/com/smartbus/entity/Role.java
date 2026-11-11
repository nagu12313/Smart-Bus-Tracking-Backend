package com.smartbus.entity;

/**
 * User roles in the Smart Bus Tracking System.
 * Controls access to features and dashboards.
 */
public enum Role {
    ADMIN,         // Full system access
    SCHOOL_ADMIN,  // School-level admin access
    DRIVER,        // Driver dashboard + trip controls
    STUDENT,       // Student view: own bus + tracking
    PARENT,        // Parent view: child's bus + tracking
    TEST_USER      // Dedicated mock live tracking test user (development only)
}
