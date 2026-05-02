package com.pvpindex.network.node;

import java.time.Instant;
import java.util.Objects;

public final class ProxyNode {

    private final String proxyId;
    private final String region;
    private volatile Instant lastHeartbeat;
    private volatile boolean online;
    private volatile int playerCount;

    public ProxyNode(String proxyId, String region) {
        this.proxyId = Objects.requireNonNull(proxyId);
        this.region = region != null ? region : "default";
        this.lastHeartbeat = Instant.now();
        this.online = true;
    }

    public String proxyId() { return proxyId; }
    public String region() { return region; }
    public Instant lastHeartbeat() { return lastHeartbeat; }
    public boolean isOnline() { return online; }
    public int playerCount() { return playerCount; }

    public void heartbeat(int playerCount) {
        this.lastHeartbeat = Instant.now();
        this.online = true;
        this.playerCount = playerCount;
    }

    public void markOffline() {
        this.online = false;
    }

    public boolean isTimedOut(int timeoutSeconds) {
        return Instant.now().minusSeconds(timeoutSeconds).isAfter(lastHeartbeat);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyNode that)) return false;
        return proxyId.equals(that.proxyId);
    }

    @Override
    public int hashCode() { return proxyId.hashCode(); }

    @Override
    public String toString() {
        return "ProxyNode{id=" + proxyId + ", region=" + region
                + ", online=" + online + ", players=" + playerCount + "}";
    }
}
