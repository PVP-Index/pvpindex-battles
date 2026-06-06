package com.pvpindex.battles.placeholder;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.identifier.WorldIdentifier;
import com.pvpindex.battles.identifier.WorldNormalizer;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.reward.VaultRewardService;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion registering the {@code %pvpindex_…%} namespace.
 *
 * <h3>Available placeholders</h3>
 * <pre>
 *  Live state
 *  ──────────
 *  %pvpindex_in_battle%          true/false. player is in an active battle
 *  %pvpindex_queued%             true/false. player is in matchmaking queue
 *  %pvpindex_queued_mode%        mode id they are queued for, or "none"
 *
 *  Elo (populated from battle results; refreshed from API every 5 min)
 *  ───────────────────────────────────────────────────────────────────
 *  %pvpindex_elo%                OVERALL elo
 *  %pvpindex_elo_<mode>%         elo for a specific mode  (e.g. elo_sword)
 *  %pvpindex_elo_change%         signed delta from last battle (e.g. +18, -12)
 *
 *  Ladder rank (fetched from API;. until first result)
 *  ─────────────────────────────────────────────────────
 *  %pvpindex_rank%               OVERALL rank position (e.g. #42)
 *  %pvpindex_rank_<mode>%        rank for a specific mode
 *
 *  Counters (accumulated in-memory since the plugin loaded)
 *  ─────────────────────────────────────────────────────────
 *  %pvpindex_wins%
 *  %pvpindex_losses%
 *  %pvpindex_draws%
 *  %pvpindex_kd%                 wins ÷ losses (2 d.p.), or wins if losses = 0
 * </pre>
 */
public final class PvPIndexExpansion extends PlaceholderExpansion {

    private final PlayerStatCache statCache;
    private final BattleService battleService;
    private final BattleQueueService queueService;
    private WorldNormalizer worldNormalizer;
    private VaultRewardService vaultRewardService;

    public PvPIndexExpansion(
            PlayerStatCache statCache,
            BattleService battleService,
            BattleQueueService queueService) {
        this.statCache = statCache;
        this.battleService = battleService;
        this.queueService = queueService;
    }

    public void setWorldNormalizer(WorldNormalizer worldNormalizer) {
        this.worldNormalizer = worldNormalizer;
    }

    public void setVaultRewardService(VaultRewardService vaultRewardService) {
        this.vaultRewardService = vaultRewardService;
    }

    @Override public @NotNull String getIdentifier() { return "pvpindex"; }
    @Override public @NotNull String getAuthor()     { return "PvPIndex"; }
    @Override public @NotNull String getVersion()    { return "1.0.2"; }
    /** Keep the expansion registered across PlaceholderAPI reloads. */
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";

        PlayerStatCache.Entry entry = statCache.getOrCreate(offlinePlayer.getUniqueId());

        // Trigger a non-blocking rank refresh when the player is online.
        Player online = offlinePlayer.getPlayer();
        if (online != null && online.isOnline() && offlinePlayer.getName() != null) {
            statCache.refreshRanksIfStale(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        }

        String p = params.toLowerCase();

        // ── Live state ────────────────────────────────────────────────────────

        if (p.equals("in_battle")) {
            return battleService.hasActiveBattle(offlinePlayer.getUniqueId()) ? "true" : "false";
        }
        if (p.equals("queued")) {
            return queueService.isQueued(offlinePlayer.getUniqueId()) ? "true" : "false";
        }
        if (p.equals("queued_mode")) {
            return queueService.getQueuedMode(offlinePlayer.getUniqueId()).orElse("none");
        }

        // ── Battle type placeholders ─────────────────────────────────────────

        if (p.equals("battle_type") || p.equals("battle_type_normalized")) {
            return resolveBattleType(offlinePlayer.getUniqueId(), true);
        }
        if (p.equals("battle_type_raw")) {
            return resolveBattleType(offlinePlayer.getUniqueId(), false);
        }

        // ── Counters ──────────────────────────────────────────────────────────

        if (p.equals("wins"))   return String.valueOf(entry.wins.get());
        if (p.equals("losses")) return String.valueOf(entry.losses.get());
        if (p.equals("draws"))  return String.valueOf(entry.draws.get());

        if (p.equals("kd")) {
            int w = entry.wins.get();
            int l = entry.losses.get();
            return l == 0 ? String.valueOf(w) : String.format("%.2f", (double) w / l);
        }

        // ── Elo change ────────────────────────────────────────────────────────

        if (p.equals("elo_change")) {
            int delta = entry.lastEloChange.get();
            return delta >= 0 ? "+" + delta : String.valueOf(delta);
        }

        // ── Elo values: %pvpindex_elo% or %pvpindex_elo_<mode>% ─────────────
        // Guard: "elo_change" is already handled above, so "elo_" prefix here
        // is always a mode name.

        if (p.equals("elo")) {
            return eloStr(entry, "OVERALL");
        }
        if (p.startsWith("elo_")) {
            return eloStr(entry, params.substring(4).toUpperCase());
        }

        // ── Rank values: %pvpindex_rank% or %pvpindex_rank_<mode>% ───────────

        if (p.equals("rank")) {
            return rankStr(entry, "OVERALL");
        }
        if (p.startsWith("rank_")) {
            return rankStr(entry, params.substring(5).toUpperCase());
        }

        // -- Vault reward placeholders --

        if (p.equals("reward_last")) {
            if (vaultRewardService == null) return "0.00";
            return String.format("%.2f", vaultRewardService.getLastReward(offlinePlayer.getUniqueId()));
        }
        if (p.equals("streak")) {
            if (vaultRewardService == null) return "0";
            return String.valueOf(vaultRewardService.getStreak(offlinePlayer.getUniqueId()));
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String eloStr(PlayerStatCache.Entry entry, String mode) {
        Integer v = entry.elo.get(mode);
        return v != null ? String.valueOf(v) : "-";
    }

    private static String rankStr(PlayerStatCache.Entry entry, String mode) {
        Integer v = entry.rank.get(mode);
        return v != null ? "#" + v : "-";
    }

    private String resolveBattleType(java.util.UUID playerUuid, boolean normalized) {
        Optional<BattleSession> session = battleService.findActiveBattleFor(playerUuid);
        if (session.isEmpty()) return "none";

        String gameModeId = session.get().getGameModeId();
        if (gameModeId == null || gameModeId.isBlank()) return "none";

        if (normalized && worldNormalizer != null) {
            Optional<WorldIdentifier> id = worldNormalizer.getByRaw(gameModeId);
            return id.map(WorldIdentifier::normalizedId).orElse(gameModeId);
        }
        return gameModeId;
    }
}
