package com.pvpindex.velocity.config;

import com.pvpindex.network.NetworkConfig;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public final class VelocityPluginConfig {

    private final String paperSecret;
    private final List<String> monitoredServers;
    private final boolean debug;
    private final NetworkConfig networkConfig;

    private VelocityPluginConfig(String paperSecret, List<String> monitoredServers,
                                 boolean debug, NetworkConfig networkConfig) {
        this.paperSecret = paperSecret;
        this.monitoredServers = monitoredServers;
        this.debug = debug;
        this.networkConfig = networkConfig;
    }

    public static VelocityPluginConfig load(Path dataDirectory, ProxyServer server, Logger logger) {
        Path configFile = dataDirectory.resolve("config.properties");

        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(dataDirectory);
                try (InputStream in = VelocityPluginConfig.class.getResourceAsStream("/config.properties")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                        logger.info("[PvPIndex] Default config.properties written to " + configFile);
                    }
                }
            } catch (IOException e) {
                logger.warning("[PvPIndex] Could not write default config.properties: " + e.getMessage());
            }
        }

        Properties props = new Properties();
        if (Files.exists(configFile)) {
            try (var reader = Files.newBufferedReader(configFile)) {
                props.load(reader);
            } catch (IOException e) {
                logger.warning("[PvPIndex] Failed to load config.properties: " + e.getMessage());
            }
        }

        String secret = props.getProperty("paper_secret", "");
        boolean debug = Boolean.parseBoolean(props.getProperty("debug", "false"));

        String raw = props.getProperty("monitored_servers", "").trim();
        List<String> monitored = raw.isEmpty()
                ? List.of()
                : List.of(raw.split("\\s*,\\s*"));

        if (secret.isBlank()) {
            logger.warning("[PvPIndex] 'paper_secret' is not set. plugin message authentication is DISABLED. "
                    + "Set the same secret in both config.properties and Paper's config.yml.");
        }

        NetworkConfig networkConfig = parseNetworkConfig(props, logger);

        return new VelocityPluginConfig(secret, monitored, debug, networkConfig);
    }

    private static NetworkConfig parseNetworkConfig(Properties props, Logger logger) {
        boolean enabled = Boolean.parseBoolean(props.getProperty("network.enabled", "false"));
        String proxyId = props.getProperty("network.proxy_id", "proxy-1");

        String sharedServersRaw = props.getProperty("network.shared_battle_servers", "").trim();
        List<String> sharedServers = sharedServersRaw.isEmpty()
                ? List.of()
                : Arrays.asList(sharedServersRaw.split("\\s*,\\s*"));

        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId(proxyId)
                .region(props.getProperty("network.region", "default"))
                .enabled(enabled)
                .redisHost(props.getProperty("network.redis.host", "localhost"))
                .redisPort(parseIntSafe(props, "network.redis.port", 6379))
                .redisPassword(props.getProperty("network.redis.password", ""))
                .redisDatabase(parseIntSafe(props, "network.redis.database", 0))
                .redisPoolSize(parseIntSafe(props, "network.redis.pool_size", 8))
                .reconnectIntervalSeconds(parseIntSafe(props, "network.reconnect_interval", 5))
                .messageTimeoutSeconds(parseIntSafe(props, "network.message_timeout", 10))
                .heartbeatIntervalSeconds(parseIntSafe(props, "network.heartbeat_interval", 15))
                .proxyTimeoutSeconds(parseIntSafe(props, "network.proxy_timeout", 45))
                .transferStrategy(props.getProperty("network.transfer_strategy", "shared_server"))
                .sharedBattleServers(sharedServers)
                .debugLogging(Boolean.parseBoolean(props.getProperty("debug", "false")))
                .build();

        List<String> errors = cfg.validate();
        if (!errors.isEmpty()) {
            for (String err : errors) {
                logger.severe("[PvPIndex] Config error: " + err);
            }
            if (enabled) {
                logger.severe("[PvPIndex] Multi-proxy network DISABLED due to config errors.");
                return NetworkConfig.builder()
                        .proxyId(proxyId)
                        .enabled(false)
                        .build();
            }
        }

        if (enabled) {
            logger.info("[PvPIndex] Multi-proxy network enabled: proxyId=" + proxyId
                    + ", region=" + cfg.region()
                    + ", redis=" + cfg.redisHost() + ":" + cfg.redisPort());
        }

        return cfg;
    }

    private static int parseIntSafe(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String paperSecret() { return paperSecret; }

    public List<String> monitoredServers() { return monitoredServers; }

    public boolean isMonitored(String serverName) {
        return monitoredServers.isEmpty() || monitoredServers.contains(serverName);
    }

    public boolean debug() { return debug; }

    public NetworkConfig networkConfig() { return networkConfig; }
}
