package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.resolveRules;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BattleBlockPlaceListener implements Listener {
    private final BattleService battleService;
    private final GameModeRegistry gameModeRegistry;

    public BattleBlockPlaceListener(BattleService battleService, GameModeRegistry gameModeRegistry) {
        this.battleService = battleService;
        this.gameModeRegistry = gameModeRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BattleSession session = battleService.findActiveBattleFor(player.getUniqueId()).orElse(null);
        if (session == null || !contains(session, player.getUniqueId())) return;
        if (!resolveRules(session, gameModeRegistry).allowBlockPlace()) {
            event.setCancelled(true);
        }
    }
}
