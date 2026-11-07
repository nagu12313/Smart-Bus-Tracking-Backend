-- ============================================================
-- Migration V4: Enterprise Real-Time GPS Hardware Tracking Schema
-- Tables: gps_devices, bus_latest_locations, bus_location_history
-- ============================================================

-- ------------------------------------------------------------
-- TABLE 1: gps_devices
-- Hardware device registry for GPS trackers installed in buses
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gps_devices (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_identifier  VARCHAR(100) NOT NULL UNIQUE,       -- IMEI / Serial (e.g. TELTONIKA-FMC130-101)
    device_name        VARCHAR(150) NOT NULL,              -- Friendly device label
    device_token       VARCHAR(255) NOT NULL,              -- Secret Pre-Shared Key for device auth
    bus_id             BIGINT UNIQUE,                      -- Exactly one bus per GPS tracker
    device_status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, STALE, OFFLINE, MAINTENANCE
    firmware_version   VARCHAR(50),
    sim_card_number    VARCHAR(30),
    battery_level      DECIMAL(5, 2) DEFAULT 100.0,
    last_seen_at       DATETIME,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE SET NULL,
    INDEX idx_dev_identifier (device_identifier),
    INDEX idx_dev_status     (device_status),
    INDEX idx_dev_last_seen  (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- TABLE 2: bus_latest_locations
-- Hot table: Exactly 1 record per bus for O(1) instant lookup
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bus_latest_locations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id         BIGINT        NOT NULL UNIQUE,          -- 1 latest record per bus
    device_id      BIGINT,                                 -- Hardware tracker that sent update
    trip_id        BIGINT,                                 -- Linked active trip if in progress
    latitude       DECIMAL(10, 7) NOT NULL,
    longitude      DECIMAL(10, 7) NOT NULL,
    speed          DECIMAL(6, 2)  DEFAULT 0.0,             -- km/h
    heading        DECIMAL(6, 2)  DEFAULT 0.0,             -- 0-360 degrees
    accuracy       DECIMAL(6, 2)  DEFAULT 5.0,             -- GPS HDOP / radius in meters
    altitude       DECIMAL(8, 2)  DEFAULT 0.0,             -- meters above sea level
    ignition_on    BOOLEAN       DEFAULT TRUE,
    gps_timestamp  DATETIME      NOT NULL,
    received_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ONLINE',-- ONLINE, ON_ROUTE, AT_STOP, OFFLINE
    FOREIGN KEY (bus_id)    REFERENCES buses(id)       ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES gps_devices(id) ON DELETE SET NULL,
    FOREIGN KEY (trip_id)   REFERENCES trips(id)       ON DELETE SET NULL,
    INDEX idx_bll_bus_status (bus_id, status),
    INDEX idx_bll_timestamp  (gps_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- TABLE 3: bus_location_history
-- Cold table: High-volume breadcrumb trail for route mapping & audits
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bus_location_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id         BIGINT        NOT NULL,
    device_id      BIGINT,
    trip_id        BIGINT,
    latitude       DECIMAL(10, 7) NOT NULL,
    longitude      DECIMAL(10, 7) NOT NULL,
    speed          DECIMAL(6, 2)  DEFAULT 0.0,
    heading        DECIMAL(6, 2)  DEFAULT 0.0,
    accuracy       DECIMAL(6, 2)  DEFAULT 5.0,
    altitude       DECIMAL(8, 2)  DEFAULT 0.0,
    gps_timestamp  DATETIME      NOT NULL,
    received_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id)    REFERENCES buses(id)       ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES gps_devices(id) ON DELETE SET NULL,
    FOREIGN KEY (trip_id)   REFERENCES trips(id)       ON DELETE SET NULL,
    INDEX idx_blh_bus_time   (bus_id, gps_timestamp DESC),
    INDEX idx_blh_trip       (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- SEED DATA: Provision GPS hardware devices for campus buses
-- ------------------------------------------------------------
INSERT INTO gps_devices (device_identifier, device_name, device_token, bus_id, device_status, firmware_version, sim_card_number, battery_level, last_seen_at)
VALUES 
('GPS-FMC130-01', 'Teltonika FMC130 - Bus 01', 'dev_token_bus01_edutrack_secure_key_2026', 1, 'ACTIVE', 'v03.28.02.Rev.00', '+919876543201', 98.5, NOW()),
('GPS-FMC130-02', 'Teltonika FMC130 - Bus 02', 'dev_token_bus02_edutrack_secure_key_2026', 2, 'ACTIVE', 'v03.28.02.Rev.00', '+919876543202', 96.0, NOW()),
('GPS-FMC130-03', 'Teltonika FMC130 - Bus 03', 'dev_token_bus03_edutrack_secure_key_2026', 3, 'ACTIVE', 'v03.28.02.Rev.00', '+919876543203', 100.0, NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ------------------------------------------------------------
-- SEED DATA: Populate initial latest locations for instant map boot
-- ------------------------------------------------------------
INSERT INTO bus_latest_locations (bus_id, device_id, latitude, longitude, speed, heading, accuracy, altitude, gps_timestamp, status)
VALUES
(1, 1, 16.3195000, 80.4525000, 38.50, 92.00, 3.50, 32.00, NOW(), 'ON_ROUTE'),
(2, 2, 16.3170000, 80.4510000, 0.00,  180.00, 4.00, 30.00, NOW(), 'AT_STOP'),
(3, 3, 16.2900000, 80.4200000, 0.00,  0.00,   5.00, 28.00, NOW(), 'ONLINE')
ON DUPLICATE KEY UPDATE 
    latitude = VALUES(latitude),
    longitude = VALUES(longitude),
    speed = VALUES(speed),
    heading = VALUES(heading),
    gps_timestamp = VALUES(gps_timestamp),
    status = VALUES(status);
