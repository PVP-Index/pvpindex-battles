package com.pvpindex.battles.replay;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.velocity.VelocityTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Polls every active battle's players on a fixed tick interval and produces a
 * dense {@link ReplayFrame} stream. This is the recorder side of the replay
 * system; for playback see {@link ReplayPlayback}.
 *
 * <p>Capturing on the server tick (rather than from packet hooks) keeps the
 * recorder server-impl agnostic. The data we sample (location, rotation,
 * velocity, equipment, effects) is exactly what the vanilla protocol sends
 * down to the client, so playback through {@link PacketReplayBridge} is
 * visually identical to the live fight.</p>
 */
public final class PacketCaptureService {
    private final Plugin plugin;
    private final ReplaySettings settings;
    private final Map<UUID, List<ReplayFrame>> frames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tickCounters = new ConcurrentHashMap<>();
    private Runnable cancelTask;

    /** Optional velocity tracker — wired after construction via {@link #setVelocityTracker}. */
    private VelocityTracker velocityTracker;

    public PacketCaptureService(Plugin plugin, ReplaySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * Wire in a {@link VelocityTracker} so the tick loop emits velocity-change
     * events alongside frame snapshots. Optional — null means no velocity events.
     */
    public void setVelocityTracker(VelocityTracker velocityTracker) {
        this.velocityTracker = velocityTracker;
    }

    public void start(java.util.function.Supplier<List<BattleSession>> activeBattles) {
        if (cancelTask != null) {
            return;
        }
        long period = Math.max(1L, 20L / Math.max(1, settings.tickRate()));
        try {
            var t = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(activeBattles.get()), period, period);
            cancelTask = t::cancel;
        } catch (UnsupportedOperationException ignored) {
            // Folia: BukkitScheduler is banned; fall back to the async scheduler.
            // The tick loop only reads concurrent state (ConcurrentHashMap) so async is safe.
            var st = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin, ignored2 -> tick(activeBattles.get()),
                    period * 50L, period * 50L, TimeUnit.MILLISECONDS);
            cancelTask = st::cancel;
        }
    }

    public void stop() {
        if (cancelTask != null) {
            cancelTask.run();
            cancelTask = null;
        }
    }

    public void beginRecording(BattleSession session) {
        frames.put(session.getUuid(), new ArrayList<>());
        tickCounters.put(session.getUuid(), 0L);
    }

    public List<ReplayFrame> finishRecording(BattleSession session) {
        tickCounters.remove(session.getUuid());
        List<ReplayFrame> captured = frames.remove(session.getUuid());
        return captured == null ? List.of() : captured;
    }

    public List<ReplayFrame> framesFor(UUID battleUuid) {
        return frames.getOrDefault(battleUuid, List.of());
    }

    private void tick(List<BattleSession> sessions) {
        for (BattleSession session : sessions) {
            List<ReplayFrame> bucket = frames.get(session.getUuid());
            if (bucket == null) {
                continue;
            }
            if (bucket.size() >= settings.maxFrames()) {
                continue;
            }
            long tick = tickCounters.merge(session.getUuid(), 1L, Long::sum);
            for (BattleParticipant participant : session.getParticipants()) {
                Player player = Bukkit.getPlayer(participant.getUuid());
                if (player == null || !player.isOnline()) continue;
                if (velocityTracker != null) {
                    velocityTracker.tick(session, player);
                }
                bucket.add(snapshot(tick, player));
            }
        }
    }

    private ReplayFrame snapshot(long tick, Player player) {
        Map<String, String> equipment = new LinkedHashMap<>();
        var inv = player.getInventory();
        equipment.put("main", describe(inv.getItemInMainHand()));
        equipment.put("off", describe(inv.getItemInOffHand()));
        equipment.put("head", describe(inv.getHelmet()));
        equipment.put("chest", describe(inv.getChestplate()));
        equipment.put("legs", describe(inv.getLeggings()));
        equipment.put("feet", describe(inv.getBoots()));

        List<Map<String, Object>> effects = new ArrayList<>();
        player.getActivePotionEffects().forEach(e -> {
            Map<String, Object> map = new HashMap<>();
            map.put("type", e.getType().getName());
            map.put("amplifier", e.getAmplifier());
            map.put("duration", e.getDuration());
            effects.add(map);
        });

        var loc = player.getLocation();
        var vel = player.getVelocity();
        return new ReplayFrame(
                tick,
                player.getUniqueId(),
                "player",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), loc.getYaw(),
                vel.getX(), vel.getY(), vel.getZ(),
                player.isOnGround(),
                player.isSneaking(),
                player.isSprinting(),
                player.isBlocking(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getFireTicks(),
                equipment,
                effects,
                List.of()
        );
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "";
        return stack.getType().name() + "x" + stack.getAmount();
    }
}
