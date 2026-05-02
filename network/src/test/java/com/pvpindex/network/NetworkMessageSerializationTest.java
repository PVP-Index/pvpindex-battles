package com.pvpindex.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void broadcastMessageRoundTrip() throws Exception {
        NetworkMessage msg = NetworkMessage.broadcast("proxy-us", NetworkMessageType.PROXY_HEARTBEAT,
                Map.of("playerCount", 42, "region", "us-east"));

        String json = mapper.writeValueAsString(msg);
        NetworkMessage deserialized = mapper.readValue(json, NetworkMessage.class);

        assertEquals(msg.messageId(), deserialized.messageId());
        assertEquals(msg.type(), deserialized.type());
        assertEquals(msg.sourceProxyId(), deserialized.sourceProxyId());
        assertNull(deserialized.targetProxyId());
        assertTrue(deserialized.isBroadcast());
        assertEquals(42, deserialized.payloadInt("playerCount", 0));
        assertEquals("us-east", deserialized.payloadString("region"));
    }

    @Test
    void targetedMessageRoundTrip() throws Exception {
        UUID playerId = UUID.randomUUID();
        NetworkMessage msg = NetworkMessage.targeted("proxy-us", "proxy-eu",
                NetworkMessageType.CHALLENGE_SEND,
                Map.of("playerId", playerId.toString(), "challengerName", "Steve"));

        String json = mapper.writeValueAsString(msg);
        NetworkMessage deserialized = mapper.readValue(json, NetworkMessage.class);

        assertEquals("proxy-us", deserialized.sourceProxyId());
        assertEquals("proxy-eu", deserialized.targetProxyId());
        assertFalse(deserialized.isBroadcast());
        assertEquals(playerId, deserialized.payloadUuid("playerId"));
        assertEquals("Steve", deserialized.payloadString("challengerName"));
    }

    @Test
    void messageIdIsNonNull() {
        NetworkMessage msg = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_REGISTER, Map.of());
        assertNotNull(msg.messageId());
        assertFalse(msg.messageId().isBlank());
    }

    @Test
    void timestampIsPositive() {
        NetworkMessage msg = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_REGISTER, Map.of());
        assertTrue(msg.timestamp() > 0);
    }

    @Test
    void payloadIntDefaultValue() {
        NetworkMessage msg = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_HEARTBEAT, Map.of());
        assertEquals(-1, msg.payloadInt("missing", -1));
    }

    @Test
    void payloadUuidReturnsNullForMissing() {
        NetworkMessage msg = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_HEARTBEAT, Map.of());
        assertNull(msg.payloadUuid("missing"));
    }

    @Test
    void equalityBasedOnMessageId() {
        NetworkMessage a = new NetworkMessage("same-id", NetworkMessageType.PROXY_REGISTER, "p1", null, 100, Map.of());
        NetworkMessage b = new NetworkMessage("same-id", NetworkMessageType.PROXY_SHUTDOWN, "p2", "p3", 200, Map.of("key", "val"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentIdsNotEqual() {
        NetworkMessage a = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_REGISTER, Map.of());
        NetworkMessage b = NetworkMessage.broadcast("p1", NetworkMessageType.PROXY_REGISTER, Map.of());
        assertNotEquals(a, b);
    }

    @Test
    void allMessageTypesSerialize() throws Exception {
        for (NetworkMessageType type : NetworkMessageType.values()) {
            NetworkMessage msg = NetworkMessage.broadcast("test", type, Map.of());
            String json = mapper.writeValueAsString(msg);
            NetworkMessage deserialized = mapper.readValue(json, NetworkMessage.class);
            assertEquals(type, deserialized.type());
        }
    }
}
