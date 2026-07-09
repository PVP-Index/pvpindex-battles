package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.round3;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.withPos;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class BattleMoveListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;

    public BattleMoveListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        BattleSession session = battleService.findActiveBattleFor(playerUuid).orElse(null);
        if (session == null || session.getStatus() != BattleStatus.ACTIVE || !contains(session, playerUuid)) return;
        Vector vel = player.getVelocity();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vx", round3(vel.getX()));
        data.put("vy", round3(vel.getY()));
        data.put("vz", round3(vel.getZ()));
        replayRecorder.record(session, "player_move", playerUuid, null,
                withPos(player, data));
    }
}
