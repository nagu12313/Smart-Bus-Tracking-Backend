-- ============================================================
-- Flyway Migration V8: Deactivate Demo Accounts & Ensure Primary Admin
-- ============================================================

-- Disable demo accounts so they cannot authenticate at sign in
UPDATE users 
SET is_active = FALSE 
WHERE email IN (
    'admin@edutrack.com',
    'driver@edutrack.com',
    'parent@edutrack.com',
    'student@edutrack.com',
    'mocktest@edutrack.com'
);

-- Ensure primary admin account is registered and active
INSERT IGNORE INTO users (name, email, password, role, is_active)
VALUES ('Administrator', 'bollanagu6868@gmail.com',
        '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK3CZXE0a',
        'ADMIN', TRUE);
