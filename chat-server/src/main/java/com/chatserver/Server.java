package com.chatserver;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.logging.*;

public class Server {
    private static final Logger logger = Logger.getLogger("Server");
    private static final int PORT = 8000;
    private static final int MAX_CONNECTIONS = 5;

    public static void main(String[] args) throws IOException, SQLException {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/chatdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPass = System.getenv().getOrDefault("DB_PASS", "senha");

        DB.connect(dbUrl, dbUser, dbPass);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONNECTIONS);
        server.setExecutor(executor);

        server.createContext("/nick", Handlers.nickHandler);
        HttpHandler groupsDispatcher = exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (method.equalsIgnoreCase("POST") && (path.equals("/groups") || path.equals("/grupos"))) {
                Handlers.createGroupHandler.handle(exchange);
            } else if (method.equalsIgnoreCase("GET") && (path.equals("/groups") || path.equals("/grupos"))) {
                Handlers.listGroupsHandler.handle(exchange);
            } else if (path.matches("/(groups|grupos)/\\d+/messages")) {
                if (method.equalsIgnoreCase("POST")) {
                    Handlers.postMessageHandler.handle(exchange);
                } else if (method.equalsIgnoreCase("GET")) {
                    Handlers.getMessagesHandler.handle(exchange);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        };
        server.createContext("/groups", groupsDispatcher);
        server.createContext("/grupos", groupsDispatcher);

        server.start();
        logger.info("Server started on port " + PORT);
    }
}