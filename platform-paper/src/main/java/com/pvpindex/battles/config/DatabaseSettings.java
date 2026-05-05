package com.pvpindex.battles.config;

public record DatabaseSettings(
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
) {
    public static DatabaseSettings defaults() {
        return new DatabaseSettings(false, "none",
                "localhost", 3306, "pvpindex", "pvpindex", "", 10, false,
                "pvpindex.db", "mongodb://localhost:27017", "pvpindex");
    }
}
