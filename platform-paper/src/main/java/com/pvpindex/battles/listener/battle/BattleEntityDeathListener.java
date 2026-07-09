package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.round3;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class BattleEntityDeathListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;

    public BattleEntityDeathListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        String worldName = event.getEntity().getWorld().getName();
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            Object arenaWorld = session.getMetadata().get("arena_world");
            if (!worldName.equals(arenaWorld)) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entity", event.getEntity().getType().name());
            Location loc = event.getEntity().getLocation();
            data.put("x", round3(loc.getX()));
            data.put("y", round3(loc.getY()));
            data.put("z", round3(loc.getZ()));
            replayRecorder.record(session, "entity_death", null, null, data);
            return;
        }
    }
}
