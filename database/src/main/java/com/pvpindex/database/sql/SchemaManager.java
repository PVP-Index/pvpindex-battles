package com.pvpindex.database.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SchemaManager {

    private SchemaManager() {}

    static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pvpindex_players (
                    uuid        VARCHAR(36) PRIMARY KEY,
                    name        VARCHAR(16) NOT NULL,
                    first_seen  BIGINT NOT NULL,
                    last_seen   BIGINT NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pvpindex_stats (
                    uuid        VARCHAR(36) NOT NULL,
                    mode_id     VARCHAR(32) NOT NULL,
                    wins        INT DEFAULT 0,
                    losses      INT DEFAULT 0,
                    kills       INT DEFAULT 0,
                    deaths      INT DEFAULT 0,
                    streak      INT DEFAULT 0,
                    best_streak INT DEFAULT 0,
                    elo         INT DEFAULT 1000,
                    PRIMARY KEY (uuid, mode_id)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pvpindex_battles (
                    battle_id    VARCHAR(36) PRIMARY KEY,
                    winner_id    VARCHAR(36),
                    mode_id      VARCHAR(32),
                    duration_ms  BIGINT,
                    timestamp_ms BIGINT NOT NULL,
                    server_name  VARCHAR(64),
                    participants TEXT
                )""");
        }
    }
}
