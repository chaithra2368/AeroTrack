CREATE DATABASE IF NOT EXISTS rocket_telemetry;
USE rocket_telemetry;

CREATE TABLE IF NOT EXISTS flights (
    flight_id INT AUTO_INCREMENT PRIMARY KEY,
    flight_name VARCHAR(120) NOT NULL,
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME NULL,
    status ENUM('CREATED','RUNNING','COMPLETED','ABORTED') NOT NULL DEFAULT 'CREATED',
    scenario VARCHAR(40) NOT NULL DEFAULT 'Normal',
    CONSTRAINT chk_flight_times CHECK (end_time IS NULL OR end_time >= start_time)
);

CREATE TABLE IF NOT EXISTS telemetry (
    telemetry_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flight_id INT NOT NULL,
    packet_no INT NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    altitude DOUBLE NOT NULL,
    velocity DOUBLE NOT NULL,
    temperature DOUBLE NOT NULL,
    battery_voltage DOUBLE NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    flight_state ENUM('PAD','ASCENT','APOGEE','DESCENT','LANDED') NOT NULL,
    CONSTRAINT fk_telemetry_flight FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    CONSTRAINT uq_flight_packet UNIQUE (flight_id, packet_no),
    CONSTRAINT chk_packet_nonnegative CHECK (packet_no >= 0),
    CONSTRAINT chk_altitude_nonnegative CHECK (altitude >= 0),
    CONSTRAINT chk_battery_nonnegative CHECK (battery_voltage >= 0),
    CONSTRAINT chk_latitude_range CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_longitude_range CHECK (longitude BETWEEN -180 AND 180),
    INDEX idx_telemetry_flight_time (flight_id, recorded_at)
);

CREATE TABLE IF NOT EXISTS events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flight_id INT NOT NULL,
    telemetry_id BIGINT NULL,
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(60) NOT NULL,
    severity ENUM('INFO','WARNING','ERROR') NOT NULL DEFAULT 'WARNING',
    message VARCHAR(255) NOT NULL,
    observed_value DOUBLE NULL,
    CONSTRAINT fk_event_flight FOREIGN KEY (flight_id) REFERENCES flights(flight_id) ON DELETE CASCADE,
    CONSTRAINT fk_event_telemetry FOREIGN KEY (telemetry_id) REFERENCES telemetry(telemetry_id) ON DELETE SET NULL,
    INDEX idx_event_flight_time (flight_id, event_time)
);

CREATE TABLE IF NOT EXISTS app_users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash CHAR(64) NOT NULL,
    role ENUM('VIEWER','ANALYST','OPERATOR','ADMINISTRATOR') NOT NULL DEFAULT 'VIEWER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- SHA-256 hash of AeroTrack@123
INSERT INTO app_users(username, password_hash, role)
VALUES('admin','1b6cce9665e1c51ba3abc43ac30534d295b94391a80368996cf3a3b762ecbec0','ADMINISTRATOR')
ON DUPLICATE KEY UPDATE username = VALUES(username);

SHOW TABLES;
