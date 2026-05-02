package com.pvpindex.battles.replay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plays a recorded {@link ReplayFile} back to one or more spectating
 * moderators. Walks the frame stream at the recorded tick rate and dispatches
 * each frame to the {@link PacketReplayBridge}, which is responsible for the
 * actual packet emission.
 */
public final class ReplayPlayback {
    private final Plugin plugin;
    private final PacketReplayBridge bridge;
    private final ReplayFile replay;
    private final Player viewer;
    private final Set<UUID> spawned = new HashSet<>();
    private BukkitTask task;
    private int cursor;
    private double speed = 1.0d;
    private boolean paused;

    public ReplayPlayback(Plugin plugin, PacketReplayBridge bridge, ReplayFile replay, Player viewer) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.replay = replay;
        this.viewer = viewer;
    }

    public void start() {
        viewer.setGameMode(GameMode.SPECTATOR);
        long period = Math.max(1L, Math.round(20.0d / Math.max(1, replay.tickRate()) / Math.max(0.1d, speed)));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : spawned) {
            bridge.destroyGhost(viewer, id);
        }
        spawned.clear();
        viewer.setGameMode(GameMode.SURVIVAL);
    }

    public void pause() { this.paused = true; }
    public void resume() { this.paused = false; }
    public void setSpeed(double speed) { this.speed = speed; }
    public void seek(int frameIndex) { this.cursor = Math.max(0, Math.min(frameIndex, replay.frames().size())); }
    public int cursor() { return cursor; }
    public int totalFrames() { return replay.frames().size(); }

    private void tick() {
        if (paused) return;
        List<ReplayFrame> frames = replay.frames();
        if (cursor >= frames.size()) {
            stop();
            return;
        }
        // Drain all frames whose recorded tick equals the current playback tick.
        long currentTick = frames.get(cursor).tick();
        while (cursor < frames.size() && frames.get(cursor).tick() == currentTick) {
            ReplayFrame frame = frames.get(cursor++);
            if (spawned.add(frame.entityUuid())) {
                bridge.spawnGhost(viewer, frame.entityUuid(), frame);
            } else {
                bridge.applyFrame(viewer, frame.entityUuid(), frame);
            }
        }
    }
}
