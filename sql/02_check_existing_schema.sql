USE rocket_telemetry;

-- Inspect actual definitions before changing an existing schema.
SHOW CREATE TABLE flights;
SHOW CREATE TABLE telemetry;
SHOW CREATE TABLE events;
SHOW CREATE TABLE app_users;

SHOW COLUMNS FROM app_users LIKE 'role';

-- If app_users already exists and role is ENUM('VIEWER','ANALYST','OPERATOR','ADMINISTRATOR'),
-- use ADMINISTRATOR for the admin role, not ADMIN.
