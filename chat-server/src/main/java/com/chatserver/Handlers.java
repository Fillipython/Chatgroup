package com.chatserver;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.*;

public class Handlers {
    private static final Logger logger = Logger.getLogger("Handlers");
    private static final Gson gson = new Gson();

    public static HttpHandler nickHandler = exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = gson.fromJson(body, Map.class);
        String name = map != null ? map.get("name") : null;
        if (name == null || name.trim().isEmpty() || name.length() > 100) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
        }
    };

    public static HttpHandler createGroupHandler = exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = gson.fromJson(body, Map.class);
        String name = map != null ? map.get("name") : null;
        if (name == null || name.trim().isEmpty() || name.length() > 100) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        try {
            int id = DB.createGroup(name);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(gson.toJson(Map.of("id", id, "name", name)).getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            logger.warning("Create group error: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
        }
    };

    public static HttpHandler listGroupsHandler = exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            List<Map<String, Object>> groups = DB.listGroups();
            String json = gson.toJson(groups);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            logger.warning("List groups error: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
        }
    };

    public static HttpHandler postMessageHandler = exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        int groupId;
        try {
            groupId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> map = gson.fromJson(body, Map.class);
        String idemKey = (String) map.get("idemKey");
        String text = (String) map.get("text");
        Double tsClientD = (Double) map.get("timestamp_client");
        String nickname = (String) map.get("nickname");
        if (idemKey == null || text == null || tsClientD == null || nickname == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        long timestampClient = tsClientD.longValue();
        try {
            boolean inserted = DB.postMessage(groupId, idemKey, nickname, text, timestampClient);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(gson.toJson(Map.of("inserted", inserted)).getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            logger.warning("Post message error: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
        }
    };

    public static HttpHandler getMessagesHandler = exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        int groupId;
        try {
            groupId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        Map<String, String> queryParams = queryToMap(uri.getQuery());
        long since = 0;
        int limit = 10;
        try {
            if (queryParams.containsKey("since")) {
                since = Long.parseLong(queryParams.get("since"));
            }
            if (queryParams.containsKey("limit")) {
                limit = Math.max(1, Math.min(200, Integer.parseInt(queryParams.get("limit"))));
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        try {
            List<Map<String, Object>> messages = DB.getMessages(groupId, since, limit);
            String json = gson.toJson(messages);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            logger.warning("Get messages error: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
        }
    };

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}