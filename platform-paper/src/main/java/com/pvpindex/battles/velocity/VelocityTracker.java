package com.pvpindex.battles.velocity;

import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Per-tick velocity delta tracker for battle participants.
 *
 * <p>Called from inside {@link com.pvpindex.battles.replay.PacketCaptureService}'s
 * sync tick loop, so all reads/writes happen on the main server thread and no
 * locking is needed beyond the volatile map. A {@code velocity_change} replay
 * event is emitted whenever the speed delta between two consecutive ticks
 * exceeds {@code threshold} (blocks/tick). Sampling is further throttled by
 * {@code intervalTicks}. a value of 2 means "sample every other tick".</p>
 *
 * <p>Entries are evicted on player quit, battle end, and via the periodic
 * cleanup pass in {@link com.pvpindex.battles.PvPIndexBattlesPlugin}.</p>
 */
public final class VelocityTracker {

    private final BattleReplayRecorder recorder;
    private final double threshold;
    private final int intervalTicks;

    /**
     * Last sampled velocity per player UUID. Written and read exclusively on
     * the main thread (inside the PacketCaptureService tick timer), so a plain
     * {@link ConcurrentHashMap} is sufficient for the cross-thread {@link #clear}
     * / {@link #clearAll} calls that may come from async contexts.
     */
    private final ConcurrentHashMap<UUID, Vector> lastVelocity = new ConcurrentHashMap<>();

    /** Tick counter per player. used to honour {@code intervalTicks}. */
    private final ConcurrentHashMap<UUID, Long> tickCount = new ConcurrentHashMap<>();

    public VelocityTracker(BattleReplayRecorder recorder, double threshold, int intervalTicks) {
        this.recorder = recorder;
        this.threshold = Math.max(0.0, threshold);
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    /**
     * Called every tick for each participant in an active battle.
     * Must be called on the main server thread.
     *
     * @param session the owning battle session (for replay recording)
     * @param player  the online participant to sample
     */
    public void tick(BattleSession session, Player player) {
        UUID uuid = player.getUniqueId();

        long count = tickCount.merge(uuid, 1L, Long::sum);
        if (count % intervalTicks != 0) {
            return;
        }

        Vector current = player.getVelocity();
        Vector prev = lastVelocity.put(uuid, current.clone());

        if (prev == null) {
            return;
        }

        double delta = current.clone().subtract(prev).length();
        if (delta < threshold) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vx", round3(current.getX()));
        data.put("vy", round3(current.getY()));
        data.put("vz", round3(current.getZ()));
        data.put("delta", round3(delta));
        data.put("speed", round3(Math.sqrt(current.getX() * current.getX() + current.getZ() * current.getZ())));
        recorder.record(session, "velocity_change", uuid, null, data);
    }

    /**
     * Remove all tracking data for a single player.
     * Safe to call from any thread.
     */
    public void clear(UUID playerUuid) {
        lastVelocity.remove(playerUuid);
        tickCount.remove(playerUuid);
    }

    /**
     * Remove all tracking data for every player.
     * Safe to call from any thread.
     */
    public void clearAll() {
        lastVelocity.clear();
        tickCount.clear();
    }

    /**
     * Evict entries for players not present in {@code activePlayers}.
     * Called by the periodic cleanup pass. Returns the number of entries evicted.
     */
    public int evictInactive(Set<UUID> activePlayers) {
        int count = 0;
        for (UUID uuid : new ArrayList<>(lastVelocity.keySet())) {
            if (!activePlayers.contains(uuid)) {
                lastVelocity.remove(uuid);
                tickCount.remove(uuid);
                count++;
            }
        }
        return count;
    }

    /** Returns the number of players currently being tracked. */
    public int trackedCount() {
        return lastVelocity.size();
    }

    /**
     * Test-friendly entry point that feeds velocity components directly without
     * requiring a live Bukkit {@link Player}.
     *
     * @param session    the battle session to record events on
     * @param playerUuid the player to simulate
     * @param vx         x-velocity component
     * @param vy         y-velocity component
     * @param vz         z-velocity component
     */
    public void tickDirect(BattleSession session, UUID playerUuid, double vx, double vy, double vz) {
        long count = tickCount.merge(playerUuid, 1L, Long::sum);
        if (count % intervalTicks != 0) {
            return;
        }

        Vector current = new Vector(vx, vy, vz);
        Vector prev = lastVelocity.put(playerUuid, current.clone());

        if (prev == null) {
            return;
        }

        double delta = current.clone().subtract(prev).length();
        if (delta < threshold) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vx", round3(vx));
        data.put("vy", round3(vy));
        data.put("vz", round3(vz));
        data.put("delta", round3(delta));
        data.put("speed", round3(Math.sqrt(vx * vx + vz * vz)));
        recorder.record(session, "velocity_change", playerUuid, null, data);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
