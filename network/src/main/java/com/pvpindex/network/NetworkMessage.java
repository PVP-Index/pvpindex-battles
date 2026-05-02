package com.pvpindex.network;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NetworkMessage {

    private final String messageId;
    private final NetworkMessageType type;
    private final String sourceProxyId;
    private final String targetProxyId;
    private final long timestamp;
    private final Map<String, Object> payload;

    @JsonCreator
    public NetworkMessage(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("type") NetworkMessageType type,
            @JsonProperty("sourceProxyId") String sourceProxyId,
            @JsonProperty("targetProxyId") String targetProxyId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("payload") Map<String, Object> payload) {
        this.messageId = Objects.requireNonNull(messageId);
        this.type = Objects.requireNonNull(type);
        this.sourceProxyId = Objects.requireNonNull(sourceProxyId);
        this.targetProxyId = targetProxyId;
        this.timestamp = timestamp;
        this.payload = payload != null ? payload : Map.of();
    }

    public static NetworkMessage broadcast(String sourceProxyId, NetworkMessageType type, Map<String, Object> payload) {
        return new NetworkMessage(
                UUID.randomUUID().toString(),
                type,
                sourceProxyId,
                null,
                Instant.now().toEpochMilli(),
                payload
        );
    }

    public static NetworkMessage targeted(String sourceProxyId, String targetProxyId, NetworkMessageType type, Map<String, Object> payload) {
        return new NetworkMessage(
                UUID.randomUUID().toString(),
                type,
                sourceProxyId,
                targetProxyId,
                Instant.now().toEpochMilli(),
                payload
        );
    }

    public String messageId() { return messageId; }
    public NetworkMessageType type() { return type; }
    public String sourceProxyId() { return sourceProxyId; }
    public String targetProxyId() { return targetProxyId; }
    public long timestamp() { return timestamp; }
    public Map<String, Object> payload() { return payload; }

    @JsonIgnore
    public boolean isBroadcast() { return targetProxyId == null; }

    public String payloadString(String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }

    public UUID payloadUuid(String key) {
        String s = payloadString(key);
        return s != null ? UUID.fromString(s) : null;
    }

    public int payloadInt(String key, int defaultValue) {
        Object v = payload.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkMessage that)) return false;
        return messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() { return messageId.hashCode(); }

    @Override
    public String toString() {
        return "NetworkMessage{type=" + type + ", src=" + sourceProxyId
                + ", dst=" + (targetProxyId != null ? targetProxyId : "BROADCAST")
                + ", id=" + messageId + "}";
    }

    // Jackson getters for serialization
    @JsonProperty("messageId") public String getMessageId() { return messageId; }
    @JsonProperty("type") public NetworkMessageType getType() { return type; }
    @JsonProperty("sourceProxyId") public String getSourceProxyId() { return sourceProxyId; }
    @JsonProperty("targetProxyId") public String getTargetProxyId() { return targetProxyId; }
    @JsonProperty("timestamp") public long getTimestamp() { return timestamp; }
    @JsonProperty("payload") public Map<String, Object> getPayload() { return payload; }
}
