package com.pvpindex.network.node;

import java.time.Instant;
import java.util.Objects;

public class NetworkNode {

    private final String nodeId;
    private final NodeType nodeType;
    private final String region;
    private volatile Instant lastHeartbeat;
    private volatile boolean online;
    private volatile int playerCount;

    public NetworkNode(String nodeId, NodeType nodeType, String region) {
        this.nodeId = Objects.requireNonNull(nodeId);
        this.nodeType = Objects.requireNonNull(nodeType);
        this.region = region != null ? region : "default";
        this.lastHeartbeat = Instant.now();
        this.online = true;
    }

    /** Backward-compatible constructor defaulting to PROXY type. */
    public NetworkNode(String nodeId, String region) {
        this(nodeId, NodeType.PROXY, region);
    }

    public String nodeId() { return nodeId; }
    /** @deprecated Use {@link #nodeId()} */
    @Deprecated
    public String proxyId() { return nodeId; }
    public NodeType nodeType() { return nodeType; }
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
        Instant threshold = Instant.now().minusSeconds(timeoutSeconds);
        return !threshold.isBefore(lastHeartbeat);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkNode that)) return false;
        return nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() { return nodeId.hashCode(); }

    @Override
    public String toString() {
        return "NetworkNode{id=" + nodeId + ", type=" + nodeType + ", region=" + region
                + ", online=" + online + ", players=" + playerCount + "}";
    }
}
