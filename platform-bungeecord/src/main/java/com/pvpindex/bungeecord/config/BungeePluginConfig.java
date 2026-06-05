package com.pvpindex.bungeecord.config;

import com.pvpindex.network.NetworkConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public final class BungeePluginConfig {

    private final String paperSecret;
    private final List<String> monitoredServers;
    private final boolean debug;
    private final NetworkConfig networkConfig;

    private BungeePluginConfig(String paperSecret, List<String> monitoredServers,
                               boolean debug, NetworkConfig networkConfig) {
        this.paperSecret = paperSecret;
        this.monitoredServers = monitoredServers;
        this.debug = debug;
        this.networkConfig = networkConfig;
    }

    public static BungeePluginConfig load(Path dataDirectory, Logger logger) {
        Path configFile = dataDirectory.resolve("config.properties");

        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(dataDirectory);
                try (InputStream in = BungeePluginConfig.class.getResourceAsStream("/config.properties")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                        logger.info("[PvPIndex] Default config.properties written to " + configFile);
                    }
                }
            } catch (IOException e) {
                logger.warning("[PvPIndex] Could not write default config: " + e.getMessage());
            }
        }

        Properties props = new Properties();
        if (Files.exists(configFile)) {
            try (var reader = Files.newBufferedReader(configFile)) {
                props.load(reader);
            } catch (IOException e) {
                logger.warning("[PvPIndex] Failed to load config: " + e.getMessage());
            }
        }

        String secret = props.getProperty("paper_secret", "");
        boolean debug = Boolean.parseBoolean(props.getProperty("debug", "false"));
        String raw = props.getProperty("monitored_servers", "").trim();
        List<String> monitored = raw.isEmpty() ? List.of() : List.of(raw.split("\\s*,\\s*"));

        if (secret.isBlank()) {
            logger.warning("[PvPIndex] 'paper_secret' is not set. authentication DISABLED.");
        }

        NetworkConfig netCfg = parseNetworkConfig(props, logger);
        return new BungeePluginConfig(secret, monitored, debug, netCfg);
    }

    private static NetworkConfig parseNetworkConfig(Properties props, Logger logger) {
        boolean enabled = Boolean.parseBoolean(props.getProperty("network.enabled", "false"));
        String proxyId = props.getProperty("network.proxy_id", "bungee-1");

        String sharedRaw = props.getProperty("network.shared_battle_servers", "").trim();
        List<String> shared = sharedRaw.isEmpty() ? List.of() : Arrays.asList(sharedRaw.split("\\s*,\\s*"));

        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId(proxyId)
                .region(props.getProperty("network.region", "default"))
                .enabled(enabled)
                .redisHost(props.getProperty("network.redis.host", "localhost"))
                .redisPort(intSafe(props, "network.redis.port", 6379))
                .redisPassword(props.getProperty("network.redis.password", ""))
                .redisDatabase(intSafe(props, "network.redis.database", 0))
                .redisPoolSize(intSafe(props, "network.redis.pool_size", 8))
                .reconnectIntervalSeconds(intSafe(props, "network.reconnect_interval", 5))
                .messageTimeoutSeconds(intSafe(props, "network.message_timeout", 10))
                .heartbeatIntervalSeconds(intSafe(props, "network.heartbeat_interval", 15))
                .proxyTimeoutSeconds(intSafe(props, "network.proxy_timeout", 45))
                .transferStrategy(props.getProperty("network.transfer_strategy", "shared_server"))
                .sharedBattleServers(shared)
                .debugLogging(Boolean.parseBoolean(props.getProperty("debug", "false")))
                .build();

        List<String> errors = cfg.validate();
        if (!errors.isEmpty()) {
            for (String err : errors) logger.severe("[PvPIndex] Config error: " + err);
            if (enabled) {
                logger.severe("[PvPIndex] Network DISABLED due to config errors.");
                return NetworkConfig.builder().proxyId(proxyId).enabled(false).build();
            }
        }

        if (enabled) {
            logger.info("[PvPIndex] Network enabled: id=" + proxyId + ", redis=" + cfg.redisHost() + ":" + cfg.redisPort());
        }
        return cfg;
    }

    private static int intSafe(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public String paperSecret() { return paperSecret; }
    public List<String> monitoredServers() { return monitoredServers; }
    public boolean isMonitored(String name) { return monitoredServers.isEmpty() || monitoredServers.contains(name); }
    public boolean debug() { return debug; }
    public NetworkConfig networkConfig() { return networkConfig; }
}
