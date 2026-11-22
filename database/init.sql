CREATE DATABASE IF NOT EXISTS chatdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chatdb;

CREATE TABLE IF NOT EXISTS grupos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    grupo_id BIGINT NOT NULL,
    idem_key VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    nickname VARCHAR(100) NOT NULL DEFAULT 'anonymous',
    timestamp_client BIGINT NOT NULL,
    timestamp_server TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (grupo_id) REFERENCES grupos(id) ON DELETE CASCADE,
    UNIQUE KEY unique_message (grupo_id, idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_messages_group_ts ON messages(grupo_id, timestamp_server);
CREATE INDEX idx_messages_since ON messages(timestamp_server);

INSERT IGNORE INTO grupos (name) VALUES ('Geral');
