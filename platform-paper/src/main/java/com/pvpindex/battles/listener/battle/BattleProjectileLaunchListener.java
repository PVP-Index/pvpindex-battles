package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class BattleProjectileLaunchListener implements Listener {
    private final BattleService battleService;

    public BattleProjectileLaunchListener(BattleService battleService) {
        this.battleService = battleService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        BattleSession session = battleService.findActiveBattleFor(player.getUniqueId()).orElse(null);
        if (session == null || !contains(session, player.getUniqueId())) return;
        if (session.getStatus() != BattleStatus.ACTIVE) {
            event.setCancelled(true);
        }
    }
}
