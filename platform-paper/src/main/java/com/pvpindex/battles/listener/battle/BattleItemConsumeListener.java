package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.classifyConsumable;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.withPos;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class BattleItemConsumeListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;

    public BattleItemConsumeListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        BattleSession session = battleService.findActiveBattleFor(player.getUniqueId()).orElse(null);
        if (session != null && contains(session, player.getUniqueId())) {
            event.setCancelled(false);
            Material mat = event.getItem().getType();
            replayRecorder.record(session, "player_consume", player.getUniqueId(), null,
                    withPos(player, Map.of("item", mat.name(), "item_type", classifyConsumable(mat))));
        }
    }
}
