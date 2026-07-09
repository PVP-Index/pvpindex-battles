package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.withPos;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BattleDamageListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;
    private final SwingMissTracker swingMissTracker;

    public BattleDamageListener(BattleService battleService, BattleReplayRecorder replayRecorder, SwingMissTracker swingMissTracker) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
        this.swingMissTracker = swingMissTracker;
    }

    public SwingMissTracker swingMissTracker() {
        return swingMissTracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        UUID targetUuid = target.getUniqueId();
        UUID attackerUuid = attacker.getUniqueId();
        BattleSession session = battleService.findActiveBattleFor(attackerUuid).orElse(null);
        if (session != null
                && session.getStatus() == BattleStatus.ACTIVE
                && contains(session, targetUuid)
                && contains(session, attackerUuid)) {
            // Ensure damage is never suppressed by world pvp=false or other plugins
            event.setCancelled(false);
            // Hit confirmed. cancel any pending miss detection for this attacker.
            swingMissTracker.removePendingMiss(attackerUuid);
            replayRecorder.record(session, "player_damage", attackerUuid, targetUuid,
                    withPos(attacker, Map.of("damage", event.getFinalDamage())));
        }
    }
}
