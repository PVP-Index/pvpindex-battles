package com.pvpindex.battles.battle;

import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.api.PvPIndexApiClient.PostResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Sends a compact heartbeat payload for all currently active battles to
 * {@code POST /api/battles/heartbeat} on a configurable tick interval.
 *
 * <p>The heartbeat lets the backend mark battles as "server offline" when the
 * Minecraft server crashes mid-fight without sending a final result. Each
 * flush is fire-and-forget: failures log at debug level and do not block
 * the next cycle.</p>
 *
 * <p>If the endpoint returns HTTP 404 the API server does not yet support this
 * feature; subsequent flushes are silently skipped to avoid log spam.</p>
 */
public final class BattleBatchScheduler {

    private final Plugin plugin;
    private final BattleService battleService;
    private final PvPIndexApiClient apiClient;
    private final int maxBatchSize;
    private final boolean debug;

    private Runnable cancelTask;
    /** Set to true after the first 404 so we stop sending pointless requests. */
    private volatile boolean endpointUnavailable = false;

    public BattleBatchScheduler(
            Plugin plugin,
            BattleService battleService,
            PvPIndexApiClient apiClient,
            int maxBatchSize,
            boolean debug) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.apiClient = apiClient;
        this.maxBatchSize = Math.max(1, maxBatchSize);
        this.debug = debug;
    }

    /**
     * Start the heartbeat timer. Safe to call multiple times — subsequent calls
     * are no-ops if the scheduler is already running.
     *
     * @param flushIntervalTicks ticks between flushes (20 ticks = 1 second)
     */
    public void start(long flushIntervalTicks) {
        if (cancelTask != null) return;
        long period = Math.max(1L, flushIntervalTicks);
        try {
            var t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, period, period);
            cancelTask = t::cancel;
        } catch (UnsupportedOperationException e) {
            // Folia: BukkitScheduler is not supported; use the async scheduler instead.
            var st = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin, ignored -> flush(), period * 50L, period * 50L, TimeUnit.MILLISECONDS);
            cancelTask = st::cancel;
        }
    }

    /** Stop the heartbeat timer. Safe to call when already stopped. */
    public void stop() {
        if (cancelTask != null) {
            cancelTask.run();
            cancelTask = null;
        }
    }

    private void flush() {
        if (endpointUnavailable) return;

        List<BattleSession> active = battleService.activeBattles();
        if (active.isEmpty()) return;

        List<Map<String, Object>> batch = active.stream()
                .limit(maxBatchSize)
                .map(s -> Map.<String, Object>of(
                        "uuid", s.getUuid().toString(),
                        "status", s.getStatus().name(),
                        "participant_count", s.getParticipants().size(),
                        "server_id", s.getServerId()))
                .collect(Collectors.toList());

        apiClient.sendHeartbeat(batch).thenAccept(result -> {
            if (result.ok()) {
                if (debug) {
                    plugin.getLogger().info("[BattleBatch] Heartbeat sent for "
                            + batch.size() + " battle(s) (HTTP " + result.statusCode() + ").");
                }
            } else if (result.statusCode() == 404 || result.statusCode() == 405) {
                endpointUnavailable = true;
                if (debug) {
                    plugin.getLogger().info("[BattleBatch] Heartbeat endpoint not available (HTTP "
                            + result.statusCode() + ") — suppressing future requests until next reload.");
                }
            } else {
                if (debug) {
                    plugin.getLogger().warning("[BattleBatch] Heartbeat failed: " + result.describe());
                }
            }
        }).exceptionally(ex -> {
            if (debug) {
                plugin.getLogger().warning("[BattleBatch] Heartbeat exception: "
                        + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
            }
            return null;
        });
    }
}
