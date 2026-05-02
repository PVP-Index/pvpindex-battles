package com.pvpindex.battles.common.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Wire-format message exchanged between the Paper backend plugin and the
 * Velocity proxy plugin over {@link PluginChannel#PROXY}.
 *
 * <p>The on-wire encoding is a single UTF-8 JSON object:
 * <pre>{"type":"BATTLE_START","secret":"abc","data":{...}}</pre>
 * </p>
 *
 * <p>The {@code secret} field is optional (empty string when not configured).
 * Velocity validates it before processing any message from a backend. This
 * prevents rogue backends from injecting false battle events.</p>
 *
 * <p>Usage on the <em>sending</em> side:</p>
 * <pre>{@code
 *   byte[] bytes = BattleMessage.encode(MAPPER, MessageType.BATTLE_START, "mysecret",
 *       Map.of("battleUuid", uuid.toString(), "serverId", "main"));
 * }</pre>
 *
 * <p>Usage on the <em>receiving</em> side:</p>
 * <pre>{@code
 *   BattleMessage msg = BattleMessage.decode(MAPPER, bytes);
 *   if (msg.isValid("mysecret") && msg.type() == MessageType.BATTLE_START) {
 *       String battleUuid = (String) msg.data().get("battleUuid");
 *   }
 * }</pre>
 */
public record BattleMessage(
        MessageType type,
        String secret,
        Map<String, Object> data
) {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // -------------------------------------------------------------------------
    // Encoding
    // -------------------------------------------------------------------------

    /**
     * Serialise this message to UTF-8 JSON bytes suitable for a plugin channel.
     */
    public byte[] encode(ObjectMapper mapper) throws IOException {
        Map<String, Object> wire = new java.util.LinkedHashMap<>();
        wire.put("type", type.name());
        wire.put("secret", secret == null ? "" : secret);
        wire.put("data", data == null ? Map.of() : data);
        return mapper.writeValueAsBytes(wire);
    }

    /**
     * Convenience factory: creates and encodes in one call.
     */
    public static byte[] encode(ObjectMapper mapper, MessageType type, String secret,
            Map<String, Object> data) throws IOException {
        return new BattleMessage(type, secret, data).encode(mapper);
    }

    // -------------------------------------------------------------------------
    // Decoding
    // -------------------------------------------------------------------------

    /**
     * Parse a plugin-channel byte payload into a {@link BattleMessage}.
     *
     * @throws IOException if the bytes are not valid JSON or are missing required fields
     */
    @SuppressWarnings("unchecked")
    public static BattleMessage decode(ObjectMapper mapper, byte[] bytes) throws IOException {
        Map<String, Object> wire = mapper.readValue(new String(bytes, StandardCharsets.UTF_8), MAP_TYPE);

        String typeName = (String) wire.get("type");
        if (typeName == null) {
            throw new IOException("Missing 'type' field in plugin message");
        }
        MessageType type;
        try {
            type = MessageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown message type: " + typeName);
        }

        String secret = (String) wire.getOrDefault("secret", "");
        Object dataObj = wire.get("data");
        Map<String, Object> data = dataObj instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        return new BattleMessage(type, secret, data);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the message's secret matches the expected one.
     * An empty expected secret disables validation (allow all).
     */
    public boolean isValid(String expectedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            return true;
        }
        return expectedSecret.equals(secret);
    }
}
