package com.pvpindex.battles.battle;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages the post-death loot cooldown phase for SMP battles. When the loser
 * dies, the winner gets a configurable window to collect dropped items before
 * the battle is cleaned up. During this phase, the winner can leave early
 * via {@code /battle leave}.
 *
 * <p>Inventory is <strong>not</strong> restored for either player after an SMP
 * battle — the winner keeps whatever they picked up and the loser respawns
 * with whatever remains (typically nothing).</p>
 */
public final class SmpLootPhaseService {

    private final Plugin plugin;
    private final BattleService battleService;
    private final PlayerStateService playerStateService;
    private final Map<UUID, LootPhase> activePhases = new ConcurrentHashMap<>();

    public SmpLootPhaseService(Plugin plugin, BattleService battleService, PlayerStateService playerStateService) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.playerStateService = playerStateService;
    }

    /** Returns {@code true} if the given battle is currently in a loot phase. */
    public boolean isInLootPhase(UUID battleUuid) {
        return activePhases.containsKey(battleUuid);
    }

    /** Returns {@code true} if the given player is the winner in an active loot phase. */
    public boolean isLootPhaseWinner(UUID playerUuid) {
        return activePhases.values().stream().anyMatch(lp -> lp.winnerUuid.equals(playerUuid));
    }

    /**
     * Start the loot cooldown phase. The battle remains "active" in the
     * session map until the phase ends so that arena cleanup doesn't run
     * prematurely.
     */
    public void startLootPhase(UUID battleUuid, UUID winnerUuid, UUID loserUuid, int durationSeconds) {
        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner == null || !winner.isOnline()) {
            battleService.endAndCleanup(battleUuid, List.of(winnerUuid));
            return;
        }

        BossBar bar = BossBar.bossBar(
                Component.text("Loot time remaining: " + durationSeconds + "s", NamedTextColor.GOLD),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS);
        winner.showBossBar(bar);

        winner.sendMessage(Component.text("You have " + durationSeconds
                + " seconds to collect your opponent's items!", NamedTextColor.GOLD));
        winner.sendMessage(Component.text("Type /battle leave to finish early.", NamedTextColor.GRAY));

        BukkitTask tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = durationSeconds;
            @Override
            public void run() {
                remaining--;
                if (remaining <= 0) {
                    endLootPhase(battleUuid);
                    return;
                }
                float progress = Math.max(0f, (float) remaining / durationSeconds);
                bar.progress(progress);
                bar.name(Component.text("Loot time remaining: " + remaining + "s", NamedTextColor.GOLD));
            }
        }, 20L, 20L);

        activePhases.put(battleUuid, new LootPhase(battleUuid, winnerUuid, loserUuid, bar, tickTask));
    }

    /**
     * End the loot phase — called when the timer expires or the winner
     * leaves early. Restores both players' locations (but not inventories)
     * and runs the normal battle cleanup pipeline.
     */
    public void endLootPhase(UUID battleUuid) {
        LootPhase phase = activePhases.remove(battleUuid);
        if (phase == null) return;

        phase.tickTask.cancel();

        Player winner = Bukkit.getPlayer(phase.winnerUuid);
        if (winner != null) {
            winner.hideBossBar(phase.bossBar);
            winner.sendMessage(Component.text("Loot phase ended.", NamedTextColor.YELLOW));
        }

        // Restore location/state for both players but skip inventory — they keep
        // whatever they have right now.
        if (playerStateService != null) {
            for (UUID uuid : List.of(phase.winnerUuid, phase.loserUuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    playerStateService.restoreWithoutInventory(p);
                }
            }
        }

        // Run the standard finish + submit pipeline (state restore inside
        // endAndCleanup is skipped because the snapshots have already been
        // consumed by restoreWithoutInventory above).
        battleService.endAndCleanup(battleUuid, List.of(phase.winnerUuid));
    }

    /**
     * If the player is a winner in a loot phase, end it early. Called from
     * the {@code /battle leave} command handler.
     *
     * @return {@code true} if a loot phase was ended
     */
    public boolean tryLeaveEarly(UUID playerUuid) {
        for (Map.Entry<UUID, LootPhase> entry : activePhases.entrySet()) {
            if (entry.getValue().winnerUuid.equals(playerUuid)) {
                endLootPhase(entry.getKey());
                return true;
            }
        }
        return false;
    }

    /** Cancel all active loot phases (used on plugin disable). */
    public void cancelAll() {
        for (UUID battleUuid : List.copyOf(activePhases.keySet())) {
            endLootPhase(battleUuid);
        }
    }

    private record LootPhase(UUID battleUuid, UUID winnerUuid, UUID loserUuid, BossBar bossBar, BukkitTask tickTask) {}
}
