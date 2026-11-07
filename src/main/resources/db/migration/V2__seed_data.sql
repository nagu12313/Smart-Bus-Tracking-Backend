-- ============================================================
-- Flyway Migration V2: Default Admin User + Sample Data
-- Password: admin123 (BCrypt hash)
-- ============================================================

-- Insert default admin user
-- BCrypt hash of "admin123"
INSERT IGNORE INTO users (name, email, password, role, is_active)
VALUES ('Administrator', 'admin@edutrack.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK3CZXE0a',
        'ADMIN', TRUE);

-- Insert default driver user
-- BCrypt hash of "driver123"
INSERT IGNORE INTO users (name, email, password, role, is_active)
VALUES ('Ramesh Kumar', 'driver@edutrack.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK3CZXE0a',
        'DRIVER', TRUE);

-- Insert default parent user
-- BCrypt hash of "parent123"
INSERT IGNORE INTO users (name, email, password, role, is_active)
VALUES ('Priya Sharma', 'parent@edutrack.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK3CZXE0a',
        'PARENT', TRUE);

-- Insert default student user
-- BCrypt hash of "student123"
INSERT IGNORE INTO users (name, email, password, role, is_active)
VALUES ('Ananya Sharma', 'student@edutrack.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK3CZXE0a',
        'STUDENT', TRUE);

-- Insert sample routes
INSERT IGNORE INTO routes (name, start_point, end_point, distance)
VALUES
  ('Guntur Central Route', 'Lakshmipuram', 'Main Campus', '12 km'),
  ('Tenali Route',         'Tenali Bus Stand', 'Main Campus', '24 km'),
  ('Mangalagiri Route',    'Mangalagiri', 'Main Campus', '19 km');

-- Insert sample stops
INSERT IGNORE INTO stops (name, latitude, longitude)
VALUES
  ('Lakshmipuram',       16.3067, 80.4365),
  ('Brodipet',           16.3100, 80.4420),
  ('Arundelpet',         16.3140, 80.4480),
  ('Tenali Bus Stand',   16.2400, 80.6300),
  ('Nallapadu',          16.2800, 80.5400),
  ('Ring Road',          16.3000, 80.4900),
  ('Mangalagiri',        16.4300, 80.5100),
  ('Autonagar',          16.4100, 80.5000),
  ('Inner Ring Road',    16.3800, 80.4800),
  ('Main Campus',        16.3400, 80.4700);

-- Link route 1 stops
INSERT IGNORE INTO route_stops (route_id, stop_id, stop_order, arrival_time)
SELECT r.id, s.id, 1, '07:00'
FROM routes r, stops s WHERE r.name='Guntur Central Route' AND s.name='Lakshmipuram';

INSERT IGNORE INTO route_stops (route_id, stop_id, stop_order, arrival_time)
SELECT r.id, s.id, 2, '07:15'
FROM routes r, stops s WHERE r.name='Guntur Central Route' AND s.name='Brodipet';

INSERT IGNORE INTO route_stops (route_id, stop_id, stop_order, arrival_time)
SELECT r.id, s.id, 3, '07:25'
FROM routes r, stops s WHERE r.name='Guntur Central Route' AND s.name='Arundelpet';

INSERT IGNORE INTO route_stops (route_id, stop_id, stop_order, arrival_time)
SELECT r.id, s.id, 4, '07:45'
FROM routes r, stops s WHERE r.name='Guntur Central Route' AND s.name='Main Campus';

-- Insert driver profile for Ramesh Kumar
INSERT IGNORE INTO drivers (user_id, license_number, phone, status)
SELECT u.id, 'AP072020001', '9876543210', 'ACTIVE'
FROM users u WHERE u.email = 'driver@edutrack.com';

-- Insert sample buses
INSERT IGNORE INTO buses (bus_number, bus_name, capacity, status, driver_id, route_id)
SELECT 'AP-07-AB-1234', 'Campus Express 01', 50, 'OFFLINE', d.id, r.id
FROM drivers d
JOIN users u ON d.user_id = u.id
JOIN routes r ON r.name = 'Guntur Central Route'
WHERE u.email = 'driver@edutrack.com';

INSERT IGNORE INTO buses (bus_number, bus_name, capacity, status)
VALUES ('AP-07-CD-5678', 'Campus Express 02', 45, 'OFFLINE');

INSERT IGNORE INTO buses (bus_number, bus_name, capacity, status)
VALUES ('AP-07-EF-9012', 'Campus Express 03', 40, 'OFFLINE');

-- Insert student profile for Ananya Sharma
INSERT IGNORE INTO students (user_id, roll_number, class_name, section, parent_name, parent_phone, bus_id, pickup_stop_id)
SELECT u.id, 'CSE2401', 'B.Tech CSE', 'A', 'Priya Sharma', '9876500011',
       b.id, s.id
FROM users u
JOIN buses b ON b.bus_number = 'AP-07-AB-1234'
JOIN stops s ON s.name = 'Lakshmipuram'
WHERE u.email = 'student@edutrack.com';
