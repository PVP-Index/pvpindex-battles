package com.pvpindex.battles.placeholder;

import com.pvpindex.battles.api.PvPIndexApiClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * In-memory cache of per-player PvP statistics used by {@link PvPIndexExpansion}.
 *
 * <p>Elo values are populated immediately after each battle finishes (via
 * {@link PlaceholderUpdateListener}). Rank positions are fetched from the
 * PvPIndex API on first use and refreshed once every {@link #RANK_TTL} to
 * avoid hammering the remote endpoint.</p>
 */
public final class PlayerStatCache {

    static final Duration RANK_TTL = Duration.ofMinutes(5);

    private final JavaPlugin plugin;
    private final PvPIndexApiClient apiClient;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public PlayerStatCache(JavaPlugin plugin, PvPIndexApiClient apiClient) {
        this.plugin = plugin;
        this.apiClient = apiClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Entry getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new Entry());
    }

    /**
     * Update elo from a completed battle participant. Safe to call from any
     * thread; all state inside {@link Entry} is thread-safe.
     */
    public void updateFromBattle(UUID uuid, String modeId, Integer eloAfter, Integer eloChange) {
        Entry e = getOrCreate(uuid);
        if (eloAfter != null) {
            e.elo.put(modeId.toUpperCase(), eloAfter);
        }
        if (eloChange != null) {
            e.lastEloChange.set(eloChange);
        }
    }

    /**
     * Triggers an async API refresh when the cached rank data is stale.
     * Non-blocking; the current (potentially stale) value stays readable
     * until the updated data arrives.
     */
    public void refreshRanksIfStale(UUID uuid, String playerName) {
        Entry entry = getOrCreate(uuid);
        if (entry.rankFetchPending
                || Instant.now().isBefore(entry.ranksLastFetched.plus(RANK_TTL))) {
            return;
        }
        entry.rankFetchPending = true;
        apiClient.fetchPlayerRankings(playerName)
                .thenAccept(data -> {
                    data.forEach((mode, re) -> {
                        entry.elo.put(mode, re.elo());
                        entry.rank.put(mode, re.rank());
                    });
                    entry.ranksLastFetched = Instant.now();
                    entry.rankFetchPending = false;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().fine("[PAPI] Rank fetch failed for " + playerName
                            + ": " + ex.getMessage());
                    entry.ranksLastFetched = Instant.now(); // back-off; don't flood on errors
                    entry.rankFetchPending = false;
                    return null;
                });
    }

    // -------------------------------------------------------------------------
    // Entry. one per UUID
    // -------------------------------------------------------------------------

    static final class Entry {
        /** Current elo per mode (upper-cased GameModeType name, e.g. "SWORD"). */
        final Map<String, Integer> elo  = new ConcurrentHashMap<>();
        /** Ladder rank position per mode (1-indexed). */
        final Map<String, Integer> rank = new ConcurrentHashMap<>();

        final AtomicInteger wins          = new AtomicInteger();
        final AtomicInteger losses        = new AtomicInteger();
        final AtomicInteger draws         = new AtomicInteger();
        /** Delta from the most recent battle (signed). */
        final AtomicInteger lastEloChange = new AtomicInteger();

        volatile Instant ranksLastFetched = Instant.EPOCH;
        volatile boolean rankFetchPending  = false;
    }
}
