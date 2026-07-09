package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public class BattleHealListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;

    public BattleHealListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BattleSession session = battleService.findActiveBattleFor(player.getUniqueId()).orElse(null);
        if (session != null && contains(session, player.getUniqueId())) {
            replayRecorder.record(session, "player_heal", player.getUniqueId(), null, Map.of("amount", event.getAmount()));
        }
    }
}
