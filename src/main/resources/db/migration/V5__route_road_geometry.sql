-- ============================================================
-- Migration V5: Road-Based Route Geometry & Bus Assignment Module
-- ============================================================

DELIMITER //
DROP PROCEDURE IF EXISTS AddRouteColumnsIfNeeded //
CREATE PROCEDURE AddRouteColumnsIfNeeded()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'routes' AND COLUMN_NAME = 'geometry_geojson') THEN
        ALTER TABLE routes ADD COLUMN geometry_geojson LONGTEXT;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'routes' AND COLUMN_NAME = 'duration_minutes') THEN
        ALTER TABLE routes ADD COLUMN duration_minutes INT DEFAULT 30;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'routes' AND COLUMN_NAME = 'version') THEN
        ALTER TABLE routes ADD COLUMN version INT DEFAULT 1;
    END IF;
END //
DELIMITER ;
CALL AddRouteColumnsIfNeeded();
DROP PROCEDURE IF EXISTS AddRouteColumnsIfNeeded;

-- ------------------------------------------------------------
-- TABLE: bus_route_assignments
-- Tracks formal assignment of bus, route, and driver with schedule
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bus_route_assignments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_id         BIGINT      NOT NULL,
    route_id       BIGINT      NOT NULL,
    driver_id      BIGINT      NULL,
    trip_type      VARCHAR(20) NOT NULL DEFAULT 'MORNING',
    start_time     VARCHAR(20) NOT NULL DEFAULT '07:30 AM',
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (bus_id)    REFERENCES buses(id)   ON DELETE CASCADE,
    FOREIGN KEY (route_id)  REFERENCES routes(id)  ON DELETE CASCADE,
    INDEX idx_bra_bus   (bus_id),
    INDEX idx_bra_route (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
