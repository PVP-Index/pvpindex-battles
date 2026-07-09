package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;
import static com.pvpindex.battles.listener.battle.BattleListenerHelper.round3;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.BattleStatus;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class BattleProjectileHitListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;

    public BattleProjectileHitListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player shooter)) return;
        UUID shooterUuid = shooter.getUniqueId();

        BattleSession session = battleService.findActiveBattleFor(shooterUuid).orElse(null);
        if (session == null || session.getStatus() != BattleStatus.ACTIVE || !contains(session, shooterUuid)) return;

        UUID hitPlayerUuid = null;
        if (event.getHitEntity() instanceof Player hitPlayer) {
            hitPlayerUuid = hitPlayer.getUniqueId();
        }

        Location loc = projectile.getLocation();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectile", projectile.getType().name());
        data.put("x", round3(loc.getX()));
        data.put("y", round3(loc.getY()));
        data.put("z", round3(loc.getZ()));
        if (event.getHitBlock() != null) {
            data.put("hit_block", event.getHitBlock().getType().name());
        }
        replayRecorder.record(session, "projectile_hit", shooterUuid, hitPlayerUuid, data);
    }
}
