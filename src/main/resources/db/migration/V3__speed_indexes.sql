-- Migration V3: High-speed Performance Indexes
CREATE INDEX idx_bl_bus_time ON bus_locations (bus_id, gps_time DESC);
CREATE INDEX idx_att_lookup ON attendance (student_id, trip_id);
CREATE INDEX idx_trips_active ON trips (bus_id, status);
