package com.pvpindex.battles.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleStatus;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.GameModeRules;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class BattleEventListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;
    private final GameModeRegistry gameModeRegistry;
    // Wired after construction (set by plugin bootstrap once services are ready).
    private BattleQueueService queueService;
    private com.pvpindex.battles.battle.SmpLootPhaseService smpLootPhaseService;

    // Last arm-swing timestamp per player (for swing-interval / autoclicker detection).
    private final Map<UUID, Long> lastSwingMs = new ConcurrentHashMap<>();
    // Pending miss detection: entry present means a swing happened but no hit has been
    // confirmed yet within the current server tick. Value = battleUuid.
    private final Map<UUID, UUID> pendingMiss = new ConcurrentHashMap<>();

    public BattleEventListener(BattleService battleService, BattleReplayRecorder replayRecorder, GameModeRegistry gameModeRegistry) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
        this.gameModeRegistry = gameModeRegistry;
    }

    /** Back-compat constructor for code that doesn't supply a registry. */
    public BattleEventListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this(battleService, replayRecorder, null);
    }

    public void setQueueService(BattleQueueService queueService) {
        this.queueService = queueService;
    }

    public void setSmpLootPhaseService(com.pvpindex.battles.battle.SmpLootPhaseService smpLootPhaseService) {
        this.smpLootPhaseService = smpLootPhaseService;
    }

    // -------------------------------------------------------------------------
    // Damage. force PvP on for active battle participants regardless of world
    // settings or other plugins that may cancel the event.
    // -------------------------------------------------------------------------

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
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() == BattleStatus.ACTIVE
                    && contains(session, targetUuid)
                    && contains(session, attackerUuid)) {
                // Ensure damage is never suppressed by world pvp=false or other plugins
                event.setCancelled(false);
                // Hit confirmed. cancel any pending miss detection for this attacker.
                pendingMiss.remove(attackerUuid);
                replayRecorder.record(session, "player_damage", attackerUuid, targetUuid,
                        withPos(attacker, Map.of("damage", event.getFinalDamage())));
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Item consumption. ensure gapples / food can never be blocked by world
    // settings or other plugins (e.g. WorldGuard respecting world.isPVP=false).
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        for (BattleSession session : battleService.activeBattles()) {
            if (contains(session, player.getUniqueId())) {
                event.setCancelled(false);
                Material mat = event.getItem().getType();
                replayRecorder.record(session, "player_consume", player.getUniqueId(), null,
                        withPos(player, Map.of("item", mat.name(), "item_type", classifyConsumable(mat))));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || !event.getItem().getType().isEdible()) return;
        Player player = event.getPlayer();
        for (BattleSession session : battleService.activeBattles()) {
            if (contains(session, player.getUniqueId())) {
                event.setCancelled(false);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Block protection. respect allowBlockBreak / allowBlockPlace from the
    // game mode rules. Defaults to disallowing both (arena safety).
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        for (BattleSession session : battleService.activeBattles()) {
            if (!contains(session, player.getUniqueId())) continue;
            if (!resolveRules(session).allowBlockBreak()) {
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        for (BattleSession session : battleService.activeBattles()) {
            if (!contains(session, player.getUniqueId())) continue;
            if (!resolveRules(session).allowBlockPlace()) {
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        String worldName = event.getLocation().getWorld().getName();
        for (BattleSession session : battleService.activeBattles()) {
            Object arenaWorld = session.getMetadata().get("arena_world");
            if (arenaWorld instanceof String aw && aw.equals(worldName)) {
                if (!resolveRules(session).allowBlockBreak()) {
                    event.blockList().clear();
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        for (BattleSession session : battleService.activeBattles()) {
            Object arenaWorld = session.getMetadata().get("arena_world");
            if (arenaWorld instanceof String aw && aw.equals(worldName)) {
                if (!resolveRules(session).allowBlockBreak()) {
                    event.blockList().clear();
                }
                return;
            }
        }
    }

    private GameModeRules resolveRules(BattleSession session) {
        if (gameModeRegistry != null) {
            Object modeIdObj = session.getMetadata().get("mode_id");
            if (modeIdObj instanceof String modeId) {
                return gameModeRegistry.findMode(modeId)
                        .map(GameModeDefinition::rules)
                        .orElse(GameModeRules.vanilla());
            }
        }
        return GameModeRules.vanilla();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID dead = player.getUniqueId();
        for (BattleSession session : battleService.activeBattles()) {
            if (!contains(session, dead)) continue;

            replayRecorder.record(session, "player_death", dead, null,
                    withPos(player, Map.of("world", player.getWorld().getName())));

            GameModeRules rules = resolveRules(session);
            boolean isSmpLoot = rules.usePlayerInventory() && rules.lootCooldownSeconds() > 0;

            if (isSmpLoot) {
                // SMP mode: let items drop naturally so the winner can loot them.
                event.setKeepInventory(false);
                event.setKeepLevel(false);
            } else {
                // Standard mode: prevent vanilla drops/loss; PlayerStateService
                // will restore the original inventory on cleanup.
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }

            // Survivors = everyone else still in the battle.
            List<UUID> winners = new ArrayList<>();
            for (BattleParticipant p : session.getParticipants()) {
                if (!p.getUuid().equals(dead)) winners.add(p.getUuid());
            }

            UUID battleUuid = session.getUuid();

			if (isSmpLoot && smpLootPhaseService != null && !winners.isEmpty()) {
				// SMP: show defeat/victory titles immediately so players get feedback.
				Bukkit.getScheduler().runTask(
						Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), () -> {
					Player loser = Bukkit.getPlayer(dead);
					if (loser != null && loser.isOnline()) {
						loser.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "Defeated",
								ChatColor.WHITE + "Your items have been dropped!", 6, 60, 14);
						loser.sendMessage(ChatColor.RED + "You lost the battle.");
						loser.playSound(loser.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.2f);
					}
					for (UUID winnerUuid : winners) {
						Player winner = Bukkit.getPlayer(winnerUuid);
						if (winner != null && winner.isOnline()) {
							winner.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "Victory!",
									ChatColor.GRAY + "Collect your opponent's items!", 6, 60, 14);
							winner.playSound(winner.getLocation(),
									org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
						}
					}
				});
                // SMP: start the loot cooldown phase instead of immediate cleanup.
                Runnable startLoot = () -> smpLootPhaseService.startLootPhase(
                        battleUuid, winners.get(0), dead, rules.lootCooldownSeconds());
                if (Bukkit.getServer() != null) {
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), startLoot);
                } else {
                    startLoot.run();
                }
            } else {
                // Standard: run cleanup on the next tick so the death event can finalise.
                Runnable end = () -> battleService.endAndCleanup(battleUuid, winners);
                if (Bukkit.getServer() != null) {
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), end);
                } else {
                    end.run();
                }
            }
            // A player can be in at most one active battle.
            return;
        }
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        battleService.activeBattles().forEach(session -> {
            if (contains(session, player.getUniqueId())) {
                replayRecorder.record(session, "player_heal", player.getUniqueId(), null, Map.of("amount", event.getAmount()));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (queueService != null) {
            queueService.leaveQuietly(playerUuid);
        }
        for (BattleSession session : battleService.activeBattles()) {
            if (!contains(session, playerUuid)) continue;
            replayRecorder.record(session, "player_leave", playerUuid, null, Map.of("reason", "quit"));

            UUID battleUuid = session.getUuid();

            // If the quitting player is the winner in an active SMP loot phase,
            // end the loot phase cleanly (restores loser, cancels boss bar,
            // etc.) instead of calling endAndCleanup directly.
            if (smpLootPhaseService != null && smpLootPhaseService.isInLootPhase(battleUuid)) {
                battleService.markPlayerLeftEarly(battleUuid, playerUuid);
                smpLootPhaseService.endLootPhase(battleUuid);
                return;
            }

            battleService.markPlayerLeftEarly(battleUuid, playerUuid);

            List<UUID> winners = new ArrayList<>();
            for (BattleParticipant p : session.getParticipants()) {
                if (!p.getUuid().equals(playerUuid)) winners.add(p.getUuid());
            }
            battleService.endAndCleanup(battleUuid, winners);
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Swing / missed-hit tracking
    // -------------------------------------------------------------------------

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

        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, playerUuid)) continue;

            long nowMs = System.currentTimeMillis();
            Long prev = lastSwingMs.put(playerUuid, nowMs);
            long msSinceLast = (prev == null) ? -1L : (nowMs - prev);

            replayRecorder.record(session, "player_swing", playerUuid, null,
                    withPos(player, Map.of("hand", "main", "ms_since_last_swing", msSinceLast)));

            pendingMiss.put(playerUuid, session.getUuid());
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
                    if (pendingMiss.remove(playerUuid) != null) {
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
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Player actions
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, playerUuid)) continue;
            replayRecorder.record(session, "player_jump", playerUuid, null,
                    withPos(player, Map.of()));
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, playerUuid)) continue;
            replayRecorder.record(session, "player_sprint", playerUuid, null,
                    withPos(player, Map.of("sprinting", event.isSprinting())));
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, playerUuid)) continue;
            replayRecorder.record(session, "player_sneak", playerUuid, null,
                    withPos(player, Map.of("sneaking", event.isSneaking())));
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Movement. block-granular position log (lightweight; head rotation changes
    // are ignored to avoid per-packet overhead on high-frequency look events).
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, playerUuid)) continue;
            Vector vel = player.getVelocity();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("vx", round3(vel.getX()));
            data.put("vy", round3(vel.getY()));
            data.put("vz", round3(vel.getZ()));
            replayRecorder.record(session, "player_move", playerUuid, null,
                    withPos(player, data));
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Projectiles. record hits for arrows, tridents, snowballs, etc.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player shooter)) return;
        UUID shooterUuid = shooter.getUniqueId();

        for (BattleSession session : battleService.activeBattles()) {
            if (session.getStatus() != BattleStatus.ACTIVE) continue;
            if (!contains(session, shooterUuid)) continue;

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
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Non-player entity death. e.g. replay bridge armor stands or summoned mobs.
    // Only recorded when the entity is inside an arena world.
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Cleanup. evict stale per-player maps for players not in active battles.
    // Called by the periodic cleanup scheduler in PvPIndexBattlesPlugin.
    // -------------------------------------------------------------------------

    /**
     * Removes tracking entries for players who are no longer participants in
     * any active battle. Returns the number of evicted entries.
     */
    public int evictStalePlayers(Collection<BattleSession> activeSessions) {
        Set<UUID> activePlayers = activeSessions.stream()
                .flatMap(s -> s.getParticipants().stream().map(BattleParticipant::getUuid))
                .collect(Collectors.toSet());

        int evicted = 0;
        for (UUID uuid : new ArrayList<>(lastSwingMs.keySet())) {
            if (!activePlayers.contains(uuid)) {
                lastSwingMs.remove(uuid);
                pendingMiss.remove(uuid);
                evicted++;
            }
        }
        return evicted;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean contains(BattleSession session, UUID uuid) {
        return session.getParticipants().stream().anyMatch(p -> p.getUuid().equals(uuid));
    }

    /**
     * Merges {@code base} data with the actor's current position and look direction.
     * Coordinates are rounded to 1 decimal place to keep the replay compact.
     */
    private static Map<String, Object> withPos(Player actor, Map<String, Object> base) {
        Location loc = actor.getLocation();
        Map<String, Object> map = new LinkedHashMap<>(base);
        map.put("x",     Math.round(loc.getX()     * 10.0)  / 10.0);
        map.put("y",     Math.round(loc.getY()     * 10.0)  / 10.0);
        map.put("z",     Math.round(loc.getZ()     * 10.0)  / 10.0);
        map.put("yaw",   (double) (Math.round(loc.getYaw()   * 10.0f) / 10.0f));
        map.put("pitch", (double) (Math.round(loc.getPitch() * 10.0f) / 10.0f));
        return map;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String classifyConsumable(Material material) {
        String name = material.name();
        if (name.contains("POTION")) return "potion";
        if (material.isEdible()) return "food";
        return "other";
    }
}
