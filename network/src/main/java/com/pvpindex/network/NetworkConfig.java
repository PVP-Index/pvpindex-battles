package com.pvpindex.network;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NetworkConfig {

    private final String proxyId;
    private final String region;
    private final boolean enabled;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final int redisDatabase;
    private final int redisPoolSize;
    private final int reconnectIntervalSeconds;
    private final int messageTimeoutSeconds;
    private final int heartbeatIntervalSeconds;
    private final int proxyTimeoutSeconds;
    private final String transferStrategy;
    private final List<String> sharedBattleServers;
    private final boolean debugLogging;

    private NetworkConfig(Builder builder) {
        this.proxyId = Objects.requireNonNull(builder.proxyId, "proxyId is required");
        this.region = builder.region != null ? builder.region : "default";
        this.enabled = builder.enabled;
        this.redisHost = builder.redisHost != null ? builder.redisHost : "localhost";
        this.redisPort = builder.redisPort > 0 ? builder.redisPort : 6379;
        this.redisPassword = builder.redisPassword;
        this.redisDatabase = builder.redisDatabase;
        this.redisPoolSize = builder.redisPoolSize > 0 ? builder.redisPoolSize : 8;
        this.reconnectIntervalSeconds = builder.reconnectIntervalSeconds > 0 ? builder.reconnectIntervalSeconds : 5;
        this.messageTimeoutSeconds = builder.messageTimeoutSeconds > 0 ? builder.messageTimeoutSeconds : 10;
        this.heartbeatIntervalSeconds = builder.heartbeatIntervalSeconds > 0 ? builder.heartbeatIntervalSeconds : 15;
        this.proxyTimeoutSeconds = builder.proxyTimeoutSeconds > 0 ? builder.proxyTimeoutSeconds : 45;
        this.transferStrategy = builder.transferStrategy != null ? builder.transferStrategy : "shared_server";
        this.sharedBattleServers = builder.sharedBattleServers != null
                ? Collections.unmodifiableList(builder.sharedBattleServers)
                : Collections.emptyList();
        this.debugLogging = builder.debugLogging;
    }

    public String proxyId() { return proxyId; }
    public String region() { return region; }
    public boolean enabled() { return enabled; }
    public String redisHost() { return redisHost; }
    public int redisPort() { return redisPort; }
    public String redisPassword() { return redisPassword; }
    public int redisDatabase() { return redisDatabase; }
    public int redisPoolSize() { return redisPoolSize; }
    public int reconnectIntervalSeconds() { return reconnectIntervalSeconds; }
    public int messageTimeoutSeconds() { return messageTimeoutSeconds; }
    public int heartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public int proxyTimeoutSeconds() { return proxyTimeoutSeconds; }
    public String transferStrategy() { return transferStrategy; }
    public List<String> sharedBattleServers() { return sharedBattleServers; }
    public boolean debugLogging() { return debugLogging; }

    public List<String> validate() {
        var errors = new java.util.ArrayList<String>();
        if (proxyId.isBlank()) errors.add("network.proxy_id must not be blank");
        if (enabled) {
            if (redisHost.isBlank()) errors.add("network.redis.host must not be blank when network is enabled");
            if (redisPort < 1 || redisPort > 65535) errors.add("network.redis.port must be 1-65535");
            if (redisPoolSize < 1) errors.add("network.redis.pool_size must be >= 1");
        }
        if (!transferStrategy.equals("transfer_packet") && !transferStrategy.equals("shared_server") && !transferStrategy.equals("both")) {
            errors.add("network.transfer_strategy must be 'transfer_packet', 'shared_server', or 'both'");
        }
        return errors;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String proxyId;
        private String region;
        private boolean enabled;
        private String redisHost;
        private int redisPort;
        private String redisPassword;
        private int redisDatabase;
        private int redisPoolSize;
        private int reconnectIntervalSeconds;
        private int messageTimeoutSeconds;
        private int heartbeatIntervalSeconds;
        private int proxyTimeoutSeconds;
        private String transferStrategy;
        private List<String> sharedBattleServers;
        private boolean debugLogging;

        public Builder proxyId(String v) { this.proxyId = v; return this; }
        public Builder region(String v) { this.region = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder redisHost(String v) { this.redisHost = v; return this; }
        public Builder redisPort(int v) { this.redisPort = v; return this; }
        public Builder redisPassword(String v) { this.redisPassword = v; return this; }
        public Builder redisDatabase(int v) { this.redisDatabase = v; return this; }
        public Builder redisPoolSize(int v) { this.redisPoolSize = v; return this; }
        public Builder reconnectIntervalSeconds(int v) { this.reconnectIntervalSeconds = v; return this; }
        public Builder messageTimeoutSeconds(int v) { this.messageTimeoutSeconds = v; return this; }
        public Builder heartbeatIntervalSeconds(int v) { this.heartbeatIntervalSeconds = v; return this; }
        public Builder proxyTimeoutSeconds(int v) { this.proxyTimeoutSeconds = v; return this; }
        public Builder transferStrategy(String v) { this.transferStrategy = v; return this; }
        public Builder sharedBattleServers(List<String> v) { this.sharedBattleServers = v; return this; }
        public Builder debugLogging(boolean v) { this.debugLogging = v; return this; }

        public NetworkConfig build() { return new NetworkConfig(this); }
    }
}
