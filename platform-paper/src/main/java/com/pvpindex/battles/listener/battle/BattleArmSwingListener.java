package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.withPos;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

public class BattleArmSwingListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;
    private final SwingMissTracker swingMissTracker;

    public BattleArmSwingListener(BattleService battleService, BattleReplayRecorder replayRecorder, SwingMissTracker swingMissTracker) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
        this.swingMissTracker = swingMissTracker;
    }

    /**
     * Fired for every arm-swing (hit or miss). Records the swing and schedules a
     * 1-tick deferred task: if no {@code EntityDamageByEntityEvent} fires for this
     * player within that tick, the swing is logged as a {@code player_miss}.
     * {@code ms_since_last_swing} is the primary anti-cheat metric. impossibly
     * small values (< ~100 ms) indicate an auto-clicker or reach hack.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        BattleSession session = battleService.findActiveBattleFor(playerUuid).orElse(null);
        if (session == null || session.getStatus() != BattleStatus.ACTIVE || !contains(session, playerUuid)) return;

        long nowMs = System.currentTimeMillis();
        Long prev = swingMissTracker.recordSwing(playerUuid);
        long msSinceLast = (prev == null) ? -1L : (nowMs - prev);

        replayRecorder.record(session, "player_swing", playerUuid, null,
                withPos(player, Map.of("hand", "main", "ms_since_last_swing", msSinceLast)));

        swingMissTracker.setPendingMiss(playerUuid, session.getUuid());
        // Capture position now. deferred miss check runs 1 tick later when
        // the player may have moved, but the swing origin is what matters.
        final double snapX = Math.round(player.getLocation().getX() * 10.0) / 10.0;
        final double snapY = Math.round(player.getLocation().getY() * 10.0) / 10.0;
        final double snapZ = Math.round(player.getLocation().getZ() * 10.0) / 10.0;
        final double snapYaw = Math.round(player.getLocation().getYaw() * 10.0f) / 10.0;
        final double snapPitch = Math.round(player.getLocation().getPitch() * 10.0f) / 10.0;
        var plugin = Bukkit.getPluginManager().getPlugin("PvPIndexBattles");
        if (plugin != null) {
            final BattleSession finalSession = session;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (swingMissTracker.removePendingMiss(playerUuid) != null) {
                    Map<String, Object> missData = new LinkedHashMap<>();
                    missData.put("hand", "main");
                    missData.put("ms_since_last_swing", msSinceLast);
                    missData.put("x", snapX);
                    missData.put("y", snapY);
                    missData.put("z", snapZ);
                    missData.put("yaw", snapYaw);
                    missData.put("pitch", snapPitch);
                    replayRecorder.record(finalSession, "player_miss", playerUuid, null, missData);
                }
            }, 1L);
        }
    }
}
