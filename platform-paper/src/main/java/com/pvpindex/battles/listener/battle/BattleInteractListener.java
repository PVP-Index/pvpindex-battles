package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.isPreBattleBlockedItem;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class BattleInteractListener implements Listener {
    private final BattleService battleService;

    public BattleInteractListener(BattleService battleService) {
        this.battleService = battleService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BattleSession session = battleService.findActiveBattleFor(player.getUniqueId()).orElse(null);
        if (session == null || !contains(session, player.getUniqueId())) return;

        // Items that can move the player or produce projectiles before the
        // battle starts must be blocked until the session is ACTIVE.
        if (session.getStatus() != BattleStatus.ACTIVE && isPreBattleBlockedItem(event.getItem())) {
            event.setCancelled(true);
            return;
        }

        if (event.getItem() == null || !event.getItem().getType().isEdible()) return;
        event.setCancelled(false);
    }
}
