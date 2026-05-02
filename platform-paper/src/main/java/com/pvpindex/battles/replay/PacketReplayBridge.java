package com.pvpindex.battles.replay;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Abstraction over the server's packet pipeline so {@link ReplayPlayback}
 * never depends on a specific server impl (Paper / NMS / ProtocolLib /
 * PacketEvents).
 *
 * <p>Inspirations: <a href="https://www.replaymod.com/">ReplayMod</a> and
 * Hypixel's spectator system, both of which feed
 * {@code ClientboundMoveEntity} / {@code ClientboundSetEntityData} packets to
 * a viewing client at vanilla tick rate. We model the same surface: spawn a
 * fake entity per recorded participant, then emit teleport / rotation /
 * animation / equipment updates as we walk the {@link ReplayFrame} stream.
 *
 * <p>The default implementation
 * ({@code com.pvpindex.battles.replay.bridge.BukkitReplayBridge}) uses
 * Bukkit-only APIs (entity spawn + teleport + setItem) so the plugin works
 * out of the box. A drop-in NMS / PacketEvents bridge can be registered via
 * {@link com.pvpindex.battles.PvPIndexBattlesPlugin#setReplayBridge}.</p>
 */
public interface PacketReplayBridge {

    /** Spawn a per-viewer fake entity that mirrors the recorded player. */
    void spawnGhost(Player viewer, UUID ghostId, ReplayFrame initialFrame);

    /** Apply the next frame to the previously spawned ghost for this viewer. */
    void applyFrame(Player viewer, UUID ghostId, ReplayFrame frame);

    /** Emit a one-shot animation (swing, hurt, death) for a ghost. */
    void emitAnimation(Player viewer, UUID ghostId, String animation);

    /** Despawn the ghost and free its entity id. */
    void destroyGhost(Player viewer, UUID ghostId);
}
