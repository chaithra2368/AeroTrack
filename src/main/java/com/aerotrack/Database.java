package com.aerotrack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * All SQL/JDBC code lives here. PreparedStatement is used for values supplied by the UI.
 * The connection password is read from an environment variable, not hard-coded.
 */
public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/rocket_telemetry";
    private static final String USER = "root";

    public Connection connect() throws SQLException {
        String password = System.getenv("AEROTRACK_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SQLException("Set AEROTRACK_DB_PASSWORD in the terminal.");
        }
        return DriverManager.getConnection(URL, USER, password);
    }

    public UserAccount authenticate(String username, String password) throws Exception {
        String sql = "SELECT password_hash, role FROM app_users WHERE username = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (!hash(password).equals(rs.getString("password_hash"))) return null;
                return new UserAccount(username, rs.getString("role"));
            }
        }
    }

    public int createFlight(String name, String scenario) throws SQLException {
        String sql = "INSERT INTO flights(flight_name,start_time,status,scenario) VALUES(?,NOW(),'RUNNING',?)";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, scenario);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("MySQL did not return a flight ID.");
                return keys.getInt(1);
            }
        }
    }

    public int nextPacketNumber(int flightId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(packet_no),-1)+1 FROM telemetry WHERE flight_id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void markFlight(int flightId, String status) throws SQLException {
        String sql = "UPDATE flights SET status=?, end_time=? WHERE flight_id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setTimestamp(2, "RUNNING".equals(status) ? null : Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setInt(3, flightId);
            ps.executeUpdate();
        }
    }

    public void savePacket(int flightId, TelemetryPacket packet,
                           List<AnomalyAnalyzer.Finding> findings) throws SQLException {
        String sql = """
                INSERT INTO telemetry
                (flight_id,packet_no,recorded_at,altitude,velocity,temperature,battery_voltage,latitude,longitude,flight_state)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                long telemetryId;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, flightId);
                    ps.setInt(2, packet.packetNo());
                    ps.setTimestamp(3, Timestamp.valueOf(packet.recordedAt()));
                    ps.setDouble(4, packet.altitude());
                    ps.setDouble(5, packet.velocity());
                    ps.setDouble(6, packet.temperature());
                    ps.setDouble(7, packet.batteryVoltage());
                    ps.setDouble(8, packet.latitude());
                    ps.setDouble(9, packet.longitude());
                    ps.setString(10, packet.flightState());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Missing telemetry ID.");
                        telemetryId = keys.getLong(1);
                    }
                }

                String eventSql = "INSERT INTO events(flight_id,telemetry_id,event_time,event_type,severity,message,observed_value) VALUES(?,?,NOW(),?,?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(eventSql)) {
                    for (AnomalyAnalyzer.Finding finding : findings) {
                        ps.setInt(1, flightId);
                        ps.setLong(2, telemetryId);
                        ps.setString(3, finding.type());
                        ps.setString(4, finding.severity());
                        ps.setString(5, finding.message());
                        ps.setDouble(6, finding.value());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<Object[]> loadFlights(String idFilter, String statusFilter,
                                      String phaseFilter, String tempFilter,
                                      String batteryFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT f.flight_id, f.flight_name, f.scenario, f.status, f.start_time, f.end_time,
                   COUNT(t.telemetry_id), MAX(t.altitude), AVG(t.temperature), MIN(t.battery_voltage)
            FROM flights f LEFT JOIN telemetry t ON t.flight_id=f.flight_id WHERE 1=1
            """);
        List<Object> args = new ArrayList<>();
        if (!idFilter.isBlank()) { sql.append(" AND f.flight_id=?"); args.add(Integer.parseInt(idFilter)); }
        if (!"Any status".equals(statusFilter)) { sql.append(" AND f.status=?"); args.add(statusFilter); }
        if (!phaseFilter.isBlank()) {
            sql.append(" AND EXISTS(SELECT 1 FROM telemetry x WHERE x.flight_id=f.flight_id AND x.flight_state=?)");
            args.add(phaseFilter.toUpperCase());
        }
        if (!tempFilter.isBlank()) {
            sql.append(" AND EXISTS(SELECT 1 FROM telemetry x WHERE x.flight_id=f.flight_id AND x.temperature>?)");
            args.add(Double.parseDouble(tempFilter));
        }
        if (!batteryFilter.isBlank()) {
            sql.append(" AND EXISTS(SELECT 1 FROM telemetry x WHERE x.flight_id=f.flight_id AND x.battery_voltage<?)");
            args.add(Double.parseDouble(batteryFilter));
        }
        sql.append(" GROUP BY f.flight_id,f.flight_name,f.scenario,f.status,f.start_time,f.end_time ORDER BY f.flight_id DESC");
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getTimestamp(5), rs.getTimestamp(6),
                            rs.getInt(7), rs.getObject(8), rs.getObject(9), rs.getObject(10)});
                }
            }
        }
        return rows;
    }

    public List<Object[]> loadTelemetry(int flightId) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT packet_no,recorded_at,altitude,velocity,temperature,battery_voltage,flight_state FROM telemetry WHERE flight_id=? ORDER BY packet_no";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Object[]{rs.getInt(1), rs.getTimestamp(2),
                        rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getString(7)});
            }
        }
        return rows;
    }

    public List<Object[]> loadEvents(int flightId) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT event_id,telemetry_id,event_time,event_type,severity,message,observed_value FROM events WHERE flight_id=? ORDER BY event_time";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Object[]{rs.getLong(1), rs.getObject(2), rs.getTimestamp(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7)});
            }
        }
        return rows;
    }

    public List<String[]> flightChoices() throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT flight_id,flight_name FROM flights ORDER BY flight_id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new String[]{String.valueOf(rs.getInt(1)), rs.getString(2)});
        }
        return rows;
    }

    public List<Object[]> graphValues(int flightId, String metric) throws SQLException {
        String column = switch (metric) {
            case "Velocity" -> "velocity";
            case "Temperature" -> "temperature";
            case "Battery" -> "battery_voltage";
            default -> "altitude";
        };
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT packet_no," + column + " FROM telemetry WHERE flight_id=? ORDER BY packet_no")) {
            ps.setInt(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Object[]{rs.getInt(1), rs.getDouble(2)});
            }
        }
        return rows;
    }

    public List<Object[]> compareFlights(int a, int b) throws SQLException {
        String sql = "SELECT COUNT(*),MAX(altitude),AVG(altitude),MAX(velocity),AVG(temperature),MAX(temperature),MIN(battery_voltage) FROM telemetry WHERE flight_id=?";
        String[] names = {"Packets", "Maximum altitude", "Average altitude", "Maximum velocity",
                "Average temperature", "Maximum temperature", "Minimum battery"};
        Object[] first = summary(sql, a);
        Object[] second = summary(sql, b);
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            double x = first[i] == null ? 0 : ((Number) first[i]).doubleValue();
            double y = second[i] == null ? 0 : ((Number) second[i]).doubleValue();
            result.add(new Object[]{names[i], String.format("%.2f", x),
                    String.format("%.2f", y), String.format("%.2f", x-y)});
        }
        return result;
    }

    private Object[] summary(String sql, int id) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Object[] values = new Object[7];
                for (int i = 0; i < values.length; i++) values[i] = rs.getObject(i + 1);
                return values;
            }
        }
    }

    public List<Object[]> loadUsers() throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT username,role,created_at FROM app_users ORDER BY username");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{rs.getString(1), rs.getString(2), rs.getTimestamp(3)});
        }
        return rows;
    }

    public void createUser(String username, String password, String role) throws Exception {
        String sql = "INSERT INTO app_users(username,password_hash,role) VALUES(?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash(password));
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    public long[] databaseCounts() throws SQLException {
        return new long[]{count("flights"), count("telemetry"), count("events"), count("app_users")};
    }

    private long count(String table) throws SQLException {
        // Table name is selected only from this fixed private list, never from user input.
        if (!List.of("flights", "telemetry", "events", "app_users").contains(table))
            throw new IllegalArgumentException("Unknown table");
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public static String hash(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
