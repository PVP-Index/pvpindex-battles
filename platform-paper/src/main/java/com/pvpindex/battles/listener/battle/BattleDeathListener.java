package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.resolveRules;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.withPos;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.SmpLootPhaseService;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.GameModeRules;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class BattleDeathListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;
    private final GameModeRegistry gameModeRegistry;
    private SmpLootPhaseService smpLootPhaseService;

    public BattleDeathListener(BattleService battleService, BattleReplayRecorder replayRecorder, GameModeRegistry gameModeRegistry) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
        this.gameModeRegistry = gameModeRegistry;
    }

    public void setSmpLootPhaseService(SmpLootPhaseService smpLootPhaseService) {
        this.smpLootPhaseService = smpLootPhaseService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID dead = player.getUniqueId();
        BattleSession session = battleService.findActiveBattleFor(dead).orElse(null);
        if (session == null || !contains(session, dead)) return;

        replayRecorder.record(session, "player_death", dead, null,
                withPos(player, Map.of("world", player.getWorld().getName())));

        GameModeRules rules = resolveRules(session, gameModeRegistry);
        boolean isSmpLoot = rules.usePlayerInventory() && rules.lootCooldownSeconds() > 0;

        if (isSmpLoot) {
            // SMP mode: let items drop naturally so the winner can loot them.
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        } else {
            // Standard mode: prevent vanilla drops/loss; PlayerStateService
            // will restore the original inventory on cleanup.
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }

        // Survivors = everyone else still in the battle.
        List<UUID> winners = new ArrayList<>();
        for (BattleParticipant p : session.getParticipants()) {
            if (!p.getUuid().equals(dead)) winners.add(p.getUuid());
        }

        UUID battleUuid = session.getUuid();

        if (isSmpLoot && smpLootPhaseService != null && !winners.isEmpty()) {
            // SMP: show defeat/victory titles immediately so players get feedback.
            Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), () -> {
                Player loser = Bukkit.getPlayer(dead);
                if (loser != null && loser.isOnline()) {
                    loser.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "Defeated",
                            ChatColor.WHITE + "Your items have been dropped!", 6, 60, 14);
                    loser.sendMessage(ChatColor.RED + "You lost the battle.");
                    loser.playSound(loser.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.2f);
                }
                for (UUID winnerUuid : winners) {
                    Player winner = Bukkit.getPlayer(winnerUuid);
                    if (winner != null && winner.isOnline()) {
                        winner.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "Victory!",
                                ChatColor.GRAY + "Collect your opponent's items!", 6, 60, 14);
                        winner.playSound(winner.getLocation(),
                                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            });
            // SMP: start the loot cooldown phase instead of immediate cleanup.
            Runnable startLoot = () -> smpLootPhaseService.startLootPhase(
                    battleUuid, winners.get(0), dead, rules.lootCooldownSeconds());
            if (Bukkit.getServer() != null) {
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), startLoot);
            } else {
                startLoot.run();
            }
        } else {
            // Standard: run cleanup on the next tick so the death event can finalise.
            Runnable end = () -> battleService.endAndCleanup(battleUuid, winners);
            if (Bukkit.getServer() != null) {
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), end);
            } else {
                end.run();
            }
        }
    }
}
