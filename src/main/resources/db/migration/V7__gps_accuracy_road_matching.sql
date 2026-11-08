-- ============================================================
-- Migration V7: Enterprise GPS Accuracy, Road Snapping & Geocoding Metadata
-- ============================================================

DELIMITER //
DROP PROCEDURE IF EXISTS AddGpsAccuracyColumns //
CREATE PROCEDURE AddGpsAccuracyColumns()
BEGIN
    -- Columns for bus_latest_locations
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'snapped_latitude') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN snapped_latitude DECIMAL(10, 7) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'snapped_longitude') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN snapped_longitude DECIMAL(10, 7) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'road_name') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN road_name VARCHAR(150) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'location_name') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN location_name VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'nearby_landmark') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN nearby_landmark VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'is_road_matched') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN is_road_matched BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_latest_locations' AND COLUMN_NAME = 'accuracy') THEN
        ALTER TABLE bus_latest_locations ADD COLUMN accuracy DECIMAL(6, 2) DEFAULT 5.0;
    END IF;

    -- Columns for bus_locations
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_locations' AND COLUMN_NAME = 'accuracy') THEN
        ALTER TABLE bus_locations ADD COLUMN accuracy DECIMAL(6, 2) DEFAULT 5.0;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_locations' AND COLUMN_NAME = 'snapped_latitude') THEN
        ALTER TABLE bus_locations ADD COLUMN snapped_latitude DECIMAL(10, 7) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_locations' AND COLUMN_NAME = 'snapped_longitude') THEN
        ALTER TABLE bus_locations ADD COLUMN snapped_longitude DECIMAL(10, 7) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_locations' AND COLUMN_NAME = 'location_name') THEN
        ALTER TABLE bus_locations ADD COLUMN location_name VARCHAR(255) NULL;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bus_locations' AND COLUMN_NAME = 'nearby_landmark') THEN
        ALTER TABLE bus_locations ADD COLUMN nearby_landmark VARCHAR(255) NULL;
    END IF;
END //
DELIMITER ;
CALL AddGpsAccuracyColumns();
DROP PROCEDURE IF EXISTS AddGpsAccuracyColumns;
