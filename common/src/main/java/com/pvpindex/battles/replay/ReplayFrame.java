package com.pvpindex.battles.replay;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One server-tick snapshot of a single tracked entity (player or projectile).
 *
 * <p>This is the packet-style primitive that {@link PacketCaptureService}
 * records and {@link ReplayPlayback} re-emits via the
 * {@link PacketReplayBridge}. Recording a stream of frames (location,
 * rotation, pose, equipment, animation flags) is the same data the vanilla
 * client receives for an entity, so playback at the same tick rate
 * reproduces the visual fight pixel-for-pixel.
 *
 * <p>The schema is intentionally a flat record so it serializes deterministically
 * to JSON / can later be promoted to a binary format (FlatBuffers / protobuf)
 * without touching the rest of the codebase.</p>
 */
public record ReplayFrame(
        long tick,
        UUID entityUuid,
        String entityType,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float headYaw,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean onGround,
        boolean sneaking,
        boolean sprinting,
        boolean blocking,
        double health,
        int foodLevel,
        int fireTicks,
        Map<String, String> equipment,
        List<Map<String, Object>> activeEffects,
        List<String> animations
) {
    public static ReplayFrame minimal(long tick, UUID uuid, double x, double y, double z, float yaw, float pitch) {
        return new ReplayFrame(
                tick, uuid, "player",
                x, y, z, yaw, pitch, yaw,
                0, 0, 0,
                true, false, false, false,
                20.0d, 20, 0,
                Map.of(), List.of(), List.of()
        );
    }
}
