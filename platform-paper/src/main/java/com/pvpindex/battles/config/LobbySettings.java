package com.pvpindex.battles.config;

public record LobbySettings(
        boolean enabled,
        String nodeId,
        String region,
        String velocityServerName,
        String redisHost,
        int redisPort,
        String redisPassword,
        int redisDatabase,
        int redisPoolSize
) {
    public static LobbySettings defaults() {
        return new LobbySettings(false, "lobby-us", "us", "",
                "localhost", 6379, "", 0, 4);
    }
}
