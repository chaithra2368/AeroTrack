USE rocket_telemetry;

-- Latest flights and their packet counts
SELECT f.flight_id, f.flight_name, f.status, COUNT(t.telemetry_id) AS packet_count
FROM flights f
LEFT JOIN telemetry t ON t.flight_id = f.flight_id
GROUP BY f.flight_id, f.flight_name, f.status
ORDER BY f.flight_id DESC;

-- Flight summary statistics
SELECT flight_id, COUNT(*) AS packets, MAX(altitude) AS max_altitude,
       AVG(temperature) AS avg_temperature, MIN(battery_voltage) AS min_battery
FROM telemetry
GROUP BY flight_id
ORDER BY flight_id DESC;

-- All anomaly events
SELECT event_id, flight_id, telemetry_id, event_time, event_type, severity, message, observed_value
FROM events
ORDER BY event_time DESC;

-- Find packets that cross educational thresholds
SELECT flight_id, packet_no, recorded_at, altitude, temperature, battery_voltage
FROM telemetry
WHERE temperature > 35 OR battery_voltage < 11 OR altitude > 1100
ORDER BY flight_id, packet_no;

-- Example flight-to-flight comparison: replace 1 and 2 with existing IDs
SELECT flight_id, COUNT(*) AS packets, MAX(altitude) AS max_altitude,
       AVG(temperature) AS avg_temperature, MIN(battery_voltage) AS min_battery
FROM telemetry
WHERE flight_id IN (1, 2)
GROUP BY flight_id;
