package com.pvpindex.database;

public record DatabaseConfig(
        boolean enabled,
        String type,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        int mysqlPoolSize,
        boolean mysqlSsl,
        String sqliteFile,
        String mongoUri,
        String mongoDatabase
) {}
