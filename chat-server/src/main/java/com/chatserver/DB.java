package com.chatserver;

import java.sql.*;
import java.util.*;
import java.util.logging.*;

public class DB {
    private static final Logger logger = Logger.getLogger("DB");
    private static Connection conn;

    public static void connect(String url, String user, String pass) throws SQLException {
        conn = DriverManager.getConnection(url, user, pass);
        logger.info("Connected to DB");
    }

    public static int createGroup(String name) throws SQLException {
        String sql = "INSERT INTO grupos (name) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create group");
    }

    public static List<Map<String, Object>> listGroups() throws SQLException {
        List<Map<String, Object>> groups = new ArrayList<>();
        String sql = "SELECT id, name FROM grupos";
        try (Statement st = conn.createStatement();
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
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        String sql = "SELECT nickname, text, timestamp_client, timestamp_server FROM messages WHERE grupo_id = ? AND timestamp_server > ? ORDER BY timestamp_server ASC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setTimestamp(2, new Timestamp(since));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
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