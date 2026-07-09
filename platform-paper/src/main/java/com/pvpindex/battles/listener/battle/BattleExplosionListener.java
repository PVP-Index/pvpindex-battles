package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.resolveRules;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class BattleExplosionListener implements Listener {
    private final BattleService battleService;
    private final GameModeRegistry gameModeRegistry;

    public BattleExplosionListener(BattleService battleService, GameModeRegistry gameModeRegistry) {
        this.battleService = battleService;
        this.gameModeRegistry = gameModeRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        String worldName = event.getLocation().getWorld().getName();
        for (BattleSession session : battleService.activeBattles()) {
            Object arenaWorld = session.getMetadata().get("arena_world");
            if (arenaWorld instanceof String aw && aw.equals(worldName)) {
                if (!resolveRules(session, gameModeRegistry).allowBlockBreak()) {
                    event.blockList().clear();
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        for (BattleSession session : battleService.activeBattles()) {
            Object arenaWorld = session.getMetadata().get("arena_world");
            if (arenaWorld instanceof String aw && aw.equals(worldName)) {
                if (!resolveRules(session, gameModeRegistry).allowBlockBreak()) {
                    event.blockList().clear();
                }
                return;
            }
        }
    }
}
