-- ============================================================
-- Smart School & College Bus Tracking System
-- Flyway Migration V1: Initial Database Schema
-- Database: Aiven Cloud MySQL (defaultdb)
-- Author: EduTrack Backend
-- ============================================================

-- ============================================================
-- TABLE 1: users
-- Core user table for all roles
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)        NOT NULL,
    email       VARCHAR(150)        NOT NULL UNIQUE,
    password    VARCHAR(255)        NOT NULL,
    role        VARCHAR(30)         NOT NULL,
    is_active   BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_role  (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 2: routes
-- Campus bus routes
-- ============================================================
CREATE TABLE IF NOT EXISTS routes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)        NOT NULL,
    start_point VARCHAR(150)        NOT NULL,
    end_point   VARCHAR(150)        NOT NULL,
    distance    VARCHAR(50),
    is_active   BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 3: stops
-- Individual bus stops with GPS coordinates
-- ============================================================
CREATE TABLE IF NOT EXISTS stops (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)        NOT NULL,
    address     VARCHAR(255),
    latitude    DECIMAL(10,7),
    longitude   DECIMAL(10,7),
    created_at  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 4: route_stops
-- Junction table: which stops belong to which route and in what order
-- ============================================================
CREATE TABLE IF NOT EXISTS route_stops (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id        BIGINT      NOT NULL,
    stop_id         BIGINT      NOT NULL,
    stop_order      INT         NOT NULL,
    arrival_time    VARCHAR(10),
    FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,
    FOREIGN KEY (stop_id)  REFERENCES stops(id)  ON DELETE CASCADE,
    UNIQUE KEY uq_route_stop (route_id, stop_id),
    INDEX idx_rs_route (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 5: drivers
-- Driver profiles linked to users
-- ============================================================
CREATE TABLE IF NOT EXISTS drivers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL UNIQUE,
    license_number  VARCHAR(50) NOT NULL UNIQUE,
    phone           VARCHAR(15) NOT NULL,
    address         VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_drivers_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 6: buses
-- School/college buses
-- ============================================================
CREATE TABLE IF NOT EXISTS buses (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_number      VARCHAR(20)     NOT NULL UNIQUE,
    bus_name        VARCHAR(100)    NOT NULL,
    capacity        INT             NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OFFLINE',
    driver_id       BIGINT,
    route_id        BIGINT,
    gps_device_id   VARCHAR(100),
    gps_device_token VARCHAR(255),
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL,
    FOREIGN KEY (route_id)  REFERENCES routes(id)  ON DELETE SET NULL,
    INDEX idx_buses_status    (status),
    INDEX idx_buses_driver    (driver_id),
    INDEX idx_buses_route     (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 7: students
-- Student profiles linked to users
-- ============================================================
CREATE TABLE IF NOT EXISTS students (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE,
    roll_number     VARCHAR(30)     NOT NULL UNIQUE,
    class_name      VARCHAR(60)     NOT NULL,
    section         VARCHAR(10),
    parent_name     VARCHAR(100),
    parent_phone    VARCHAR(15),
    bus_id          BIGINT,
    pickup_stop_id  BIGINT,
    drop_stop_id    BIGINT,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)       REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (bus_id)        REFERENCES buses(id)  ON DELETE SET NULL,
    FOREIGN KEY (pickup_stop_id) REFERENCES stops(id) ON DELETE SET NULL,
    FOREIGN KEY (drop_stop_id)   REFERENCES stops(id) ON DELETE SET NULL,
    INDEX idx_students_bus (bus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 8: trips
-- Records each bus trip (morning/evening run)
-- ============================================================
CREATE TABLE IF NOT EXISTS trips (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id      BIGINT      NOT NULL,
    driver_id   BIGINT      NOT NULL,
    route_id    BIGINT      NOT NULL,
    trip_type   VARCHAR(20) NOT NULL DEFAULT 'MORNING',
    status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    start_time  DATETIME,
    end_time    DATETIME,
    notes       VARCHAR(500),
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id)    REFERENCES buses(id)   ON DELETE RESTRICT,
    FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE RESTRICT,
    FOREIGN KEY (route_id)  REFERENCES routes(id)  ON DELETE RESTRICT,
    INDEX idx_trips_bus     (bus_id),
    INDEX idx_trips_status  (status),
    INDEX idx_trips_date    (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 9: bus_locations
-- GPS location history for each bus
-- ============================================================
CREATE TABLE IF NOT EXISTS bus_locations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id      BIGINT          NOT NULL,
    trip_id     BIGINT,
    latitude    DECIMAL(10,7)   NOT NULL,
    longitude   DECIMAL(10,7)   NOT NULL,
    speed       DECIMAL(6,2)    DEFAULT 0,
    heading     DECIMAL(6,2),
    gps_time    DATETIME        NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id)  REFERENCES buses(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    INDEX idx_bl_bus     (bus_id),
    INDEX idx_bl_gpstime (gps_time),
    INDEX idx_bl_trip    (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 10: attendance
-- Student pickup and drop-off records
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT      NOT NULL,
    trip_id         BIGINT      NOT NULL,
    type            VARCHAR(10) NOT NULL,
    recorded_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by     BIGINT,
    notes           VARCHAR(255),
    FOREIGN KEY (student_id)  REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id)     REFERENCES trips(id)    ON DELETE CASCADE,
    FOREIGN KEY (recorded_by) REFERENCES users(id)    ON DELETE SET NULL,
    UNIQUE KEY uq_attendance (student_id, trip_id, type),
    INDEX idx_att_student  (student_id),
    INDEX idx_att_trip     (trip_id),
    INDEX idx_att_date     (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 11: notifications
-- System notifications for all users
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    title       VARCHAR(150)    NOT NULL,
    message     VARCHAR(500)    NOT NULL,
    type        VARCHAR(50)     NOT NULL DEFAULT 'INFO',
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    trip_id     BIGINT,
    bus_id      BIGINT,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)   ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES trips(id)   ON DELETE SET NULL,
    FOREIGN KEY (bus_id)  REFERENCES buses(id)   ON DELETE SET NULL,
    INDEX idx_notif_user   (user_id),
    INDEX idx_notif_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
