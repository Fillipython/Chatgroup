package com.chatserver;

import java.sql.*;
import java.util.*;
import java.util.logging.*;

public class DB {
    private static final Logger logger = Logger.getLogger("DB");
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;

    public static void connect(String url, String user, String pass) throws SQLException {
        dbUrl = url;
        dbUser = user;
        dbPass = pass;
        try (Connection testConn = getConnection()) {
            logger.info("Connected to DB");
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    public static int createGroup(String name) throws SQLException {
        String selectSql = "SELECT id FROM grupos WHERE name = ?";
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("id");
                }
            }
            String insertSql = "INSERT INTO grupos (name) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt("id");
                    }
                }
            }
        }
        throw new SQLException("Failed to create group");
    }

    public static List<Map<String, Object>> listGroups() throws SQLException {
        List<Map<String, Object>> groups = new ArrayList<>();
        String sql = "SELECT id, name FROM grupos ORDER BY id ASC";
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> g = new HashMap<>();
                g.put("id", rs.getInt("id"));
                g.put("name", rs.getString("name"));
                groups.add(g);
            }
        }
        return groups;
    }

    public static boolean postMessage(int groupId, String idemKey, String nickname, String text, long timestampClient) throws SQLException {
        String sql = "INSERT INTO messages (grupo_id, idem_key, nickname, text, timestamp_client) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setString(2, idemKey);
            ps.setString(3, nickname);
            ps.setString(4, text);
            ps.setLong(5, timestampClient);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // idemKey duplicate, ignore (idempotency)
            return false;
        }
    }

    public static List<Map<String, Object>> getMessages(int groupId, long since, int limit) throws SQLException {
        List<Map<String, Object>> messages = new ArrayList<>();
        String sql = "SELECT id, nickname, text, timestamp_client, timestamp_server FROM messages WHERE grupo_id = ? AND timestamp_server >= ? ORDER BY timestamp_server ASC, id ASC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setTimestamp(2, new Timestamp(since));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("nickname", rs.getString("nickname"));
                    m.put("text", rs.getString("text"));
                    m.put("timestamp_client", rs.getLong("timestamp_client"));
                    m.put("timestamp_server", rs.getTimestamp("timestamp_server").getTime());
                    messages.add(m);
                }
            }
        }
        return messages;
    }
}
