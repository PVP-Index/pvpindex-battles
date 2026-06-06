package com.pvpindex.battles.queue;

import com.pvpindex.battles.arena.SpawnPoint;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.PlayerStateService;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.GameModeRules;
import com.pvpindex.battles.gamemode.KitApplier;
import com.pvpindex.battles.gamemode.KitDefinition;
import com.pvpindex.battles.util.MessageService;
import com.pvpindex.battles.world.ArenaInstance;
import com.pvpindex.battles.world.ArenaPoolService;
import com.pvpindex.battles.world.WorldGeneratorService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Manages per-mode matchmaking queues. When two players queue for the same
 * game mode a duel battle is created, an arena is generated (if a template is
 * configured for that mode), and both players are teleported to their spawn
 * points.
 */
public class BattleQueueService {

    private final JavaPlugin plugin;
    private final BattleService battleService;
    private final WorldGeneratorService worldGeneratorService;
    private final ArenaPoolService arenaPoolService;
    private final PlayerStateService playerStateService;
    private final GameModeRegistry gameModeRegistry;
    private final KitApplier kitApplier;
    private final com.pvpindex.battles.version.VersionAdapter versionAdapter;
    private final MessageService messageService;

    /** game-mode id → ordered list of queued player UUIDs */
    private final Map<String, List<UUID>> queues = new LinkedHashMap<>();
    /** player UUID → the game-mode id they are currently queued for */
    private final Map<UUID, String> playerQueue = new LinkedHashMap<>();

    public BattleQueueService(
            JavaPlugin plugin,
            BattleService battleService,
            WorldGeneratorService worldGeneratorService,
            ArenaPoolService arenaPoolService,
            PlayerStateService playerStateService,
            GameModeRegistry gameModeRegistry,
            KitApplier kitApplier,
            com.pvpindex.battles.version.VersionAdapter versionAdapter,
            MessageService messageService) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.worldGeneratorService = worldGeneratorService;
        this.arenaPoolService = arenaPoolService;
        this.playerStateService = playerStateService;
        this.gameModeRegistry = gameModeRegistry;
        this.kitApplier = kitApplier;
        this.versionAdapter = versionAdapter;
        this.messageService = messageService;
    }

    /** Legacy constructor kept for tests / older bootstraps. */
    public BattleQueueService(
            JavaPlugin plugin,
            BattleService battleService,
            WorldGeneratorService worldGeneratorService) {
        this(plugin, battleService, worldGeneratorService, null, null, null, new KitApplier(), null, null);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isQueued(UUID playerUuid) {
        return playerQueue.containsKey(playerUuid);
    }

    public Optional<String> getQueuedMode(UUID playerUuid) {
        return Optional.ofNullable(playerQueue.get(playerUuid));
    }

    public int queueSize(String modeId) {
        List<UUID> q = queues.get(modeId);
        return q == null ? 0 : q.size();
    }

    /**
     * Adds {@code player} to the queue for {@code mode}. If they are already
     * queued (any mode) or in an active battle the request is rejected.
     *
     * @return {@code true} if the player was successfully queued
     */
    public boolean join(Player player, GameModeDefinition mode) {
        if (playerQueue.containsKey(player.getUniqueId())) {
            messageService.send(player, "queue.already_queued");
            return false;
        }
        if (battleService.hasActiveBattle(player.getUniqueId())) {
            messageService.send(player, "queue.in_active_battle");
            return false;
        }

        queues.computeIfAbsent(mode.id(), k -> new ArrayList<>()).add(player.getUniqueId());
        playerQueue.put(player.getUniqueId(), mode.id());

        int pos = queueSize(mode.id());
        messageService.send(player, "queue.joined", "%mode%", mode.displayName(), "%pos%", String.valueOf(pos));

        tryMatch(mode);
        return true;
    }

    /**
     * Removes {@code player} from whatever queue they are in.
     *
     * @return {@code true} if the player was queued and has been removed
     */
    public boolean leave(Player player) {
        String modeId = playerQueue.remove(player.getUniqueId());
        if (modeId == null) return false;

        List<UUID> q = queues.get(modeId);
        if (q != null) q.remove(player.getUniqueId());

        messageService.send(player, "queue.left");
        return true;
    }

    /**
     * Removes a player from the queue by UUID without sending them a chat message.
     * Used when the player is already offline (quit event) so message delivery is
     * unreliable and unnecessary.
     *
     * @return {@code true} if the player was queued and has been removed
     */
    public boolean leaveQuietly(UUID playerUuid) {
        String modeId = playerQueue.remove(playerUuid);
        if (modeId == null) return false;
        List<UUID> q = queues.get(modeId);
        if (q != null) q.remove(playerUuid);
        return true;
    }

    /**
     * Start a direct match between two players, bypassing the queue entirely.
     * Used by the challenge system where both players are already confirmed.
     *
     * @return {@code true} if the match was started successfully
     */
    public boolean startDirect(Player p1, Player p2, GameModeDefinition mode) {
        leaveQuietly(p1.getUniqueId());
        leaveQuietly(p2.getUniqueId());

        if (battleService.hasActiveBattle(p1.getUniqueId())) {
            messageService.send(p1, "queue.already_in_battle");
            messageService.send(p2, "queue.opponent_in_battle");
            return false;
        }
        if (battleService.hasActiveBattle(p2.getUniqueId())) {
            messageService.send(p2, "queue.already_in_battle");
            messageService.send(p1, "queue.opponent_in_battle");
            return false;
        }

        startMatch(mode, p1, p2);
        return true;
    }

    // -------------------------------------------------------------------------
    // Internal match-making
    // -------------------------------------------------------------------------

    private void tryMatch(GameModeDefinition mode) {
        List<UUID> q = queues.get(mode.id());
        if (q == null || q.size() < 2) return;

        UUID p1uuid = q.remove(0);
        UUID p2uuid = q.remove(0);
        playerQueue.remove(p1uuid);
        playerQueue.remove(p2uuid);

        Player p1 = Bukkit.getPlayer(p1uuid);
        Player p2 = Bukkit.getPlayer(p2uuid);

        // If a player went offline between queuing and matching, re-queue the survivor.
        if (p1 == null || !p1.isOnline()) {
            requeueIfOnline(p2, p2uuid, mode);
            return;
        }
        if (p2 == null || !p2.isOnline()) {
            requeueIfOnline(p1, p1uuid, mode);
            return;
        }

        startMatch(mode, p1, p2);
    }

    private void requeueIfOnline(Player player, UUID uuid, GameModeDefinition mode) {
        if (player != null && player.isOnline()) {
            queues.computeIfAbsent(mode.id(), k -> new ArrayList<>()).add(0, uuid);
            playerQueue.put(uuid, mode.id());
        }
    }

    private void startMatch(GameModeDefinition mode, Player p1, Player p2) {
        messageService.send(p1, "queue.match_found", "%opponent%", p2.getName());
        messageService.send(p2, "queue.match_found", "%opponent%", p1.getName());

        // 1) Snapshot pre-battle player state so we can restore on death/quit.
        if (playerStateService != null) {
            playerStateService.save(p1);
            playerStateService.save(p2);
        }

        List<BattleParticipant> participants = List.of(
                new BattleParticipant(p1.getUniqueId(), p1.getName(), null),
                new BattleParticipant(p2.getUniqueId(), p2.getName(), null)
        );

        String templateId = mode.worldTemplate();
        boolean needsArena = templateId != null && !templateId.isBlank();

        if (!needsArena) {
            launchBattle(mode, participants, p1, p2, null);
            return;
        }

        // 2) Acquire a pre-generated arena (instant if pool is warm). Pool
        // service falls back to sync generate when the pool is empty so we
        // always get an instance. at worst with a small delay.
        Runnable acquireAndLaunch = () -> {
            ArenaInstance instance = null;
            try {
                if (arenaPoolService != null) {
                    instance = arenaPoolService.acquire(templateId).orElse(null);
                } else {
                    instance = worldGeneratorService.generate(templateId).orElse(null);
                }
                if (instance == null) {
                    plugin.getLogger().warning("[Queue] No template found for '" + templateId
                            + "'. battle will start without world generation.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Queue] Arena acquire failed for '"
                        + templateId + "': " + e.getMessage());
            }
            final ArenaInstance finalInstance = instance;
            Bukkit.getScheduler().runTask(plugin,
                    () -> launchBattle(mode, participants, p1, p2, finalInstance));
        };
        // Pool acquire is mostly cheap; run on async thread so the rare
        // sync-fallback (full world copy) doesn't freeze the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, acquireAndLaunch);
    }

    private void launchBattle(
            GameModeDefinition mode,
            List<BattleParticipant> participants,
            Player p1,
            Player p2,
            ArenaInstance instance) {

        String arenaId = instance != null ? instance.templateId() : null;
        BattleSession session = battleService.createBattle(
                BattleType.DUEL,
                mode.legacyType(),
                participants,
                arenaId,
                Map.of("queued", true, "mode_id", mode.id())
        );
        if (session == null) {
            // Creation was cancelled by a PvPIndexBattleCreateEvent listener.
            if (playerStateService != null) {
                playerStateService.restore(p1);
                playerStateService.restore(p2);
            }
            return;
        }
        if (instance != null) battleService.attachArena(session.getUuid(), instance);

        // 3) Neutralise flight and velocity before teleporting so players
        //    cannot exploit momentum to launch above the arena.
        //    Keep AllowFlight true until after teleport to prevent the
        //    server's anti-fly kick from triggering while the player is
        //    mid-air between worlds.
        for (Player p : List.of(p1, p2)) {
            p.setFlying(false);
            p.setVelocity(p.getVelocity().zero());
            p.setFallDistance(0.0f);
        }

        // 4) Teleport to arena spawn points.
        if (instance != null) {
            teleportToArena(p1, p2, instance);
        }

        // 5) Now that players are on the ground in the arena, disable flight.
        for (Player p : List.of(p1, p2)) {
            p.setAllowFlight(false);
            p.setFallDistance(0.0f);
        }

        // 6) Apply kit + initial rules state.
        GameModeRules rules = mode.rules() != null ? mode.rules() : GameModeRules.vanilla();
        applyPreBattleState(p1, mode, rules);
        applyPreBattleState(p2, mode, rules);

        // 7) Run countdown, then activate the battle and freeze-release.
        int countdown = Math.max(0, rules.countdownSeconds());
        runCountdown(session.getUuid(), p1, p2, countdown, rules);
    }

    private void applyPreBattleState(Player player, GameModeDefinition mode, GameModeRules rules) {
        // Kit
        if (kitApplier != null && mode.kitId() != null && gameModeRegistry != null) {
            Optional<KitDefinition> kit = gameModeRegistry.findKit(mode.kitId());
            kit.ifPresent(k -> kitApplier.apply(player, k));
        }
        // Rules
        player.setGameMode(GameMode.SURVIVAL);
        // Disable flight before anything else to prevent momentum-based
        // height exploits when a player jumps right before the battle.
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setVelocity(player.getVelocity().zero());
        var maxAttr = player.getAttribute(versionAdapter.getMaxHealthAttribute());
        if (maxAttr != null) maxAttr.setBaseValue(Math.max(1.0, rules.startHealth()));
        player.setHealth(Math.max(1.0, rules.startHealth()));
        player.setFoodLevel(Math.max(0, Math.min(20, rules.startFoodLevel())));
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setNoDamageTicks(0);
        player.setFallDistance(0.0f);

        // Freeze during countdown via slowness + jump-boost negative.
        PotionEffectType slowness = versionAdapter.getEffect("slowness");
        PotionEffectType jumpBoost = versionAdapter.getEffect("jump_boost");
        if (slowness != null) {
            player.addPotionEffect(new PotionEffect(slowness, 20 * 30, 250, false, false, false));
        }
        if (jumpBoost != null) {
            player.addPotionEffect(new PotionEffect(jumpBoost, 20 * 30, 250, false, false, false));
        }
    }

    private void runCountdown(UUID battleUuid, Player p1, Player p2, int seconds, GameModeRules rules) {
        if (seconds <= 0) {
            beginActiveBattle(battleUuid, p1, p2, rules);
            return;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                // Abort if either player disconnected during the countdown.
                if (!p1.isOnline() || !p2.isOnline()) {
                    cancel();
                    // Notify the player still online.
                    Player online = p1.isOnline() ? p1 : (p2.isOnline() ? p2 : null);
                    if (online != null) {
                        messageService.send(online, "queue.opponent_disconnected");
                    }
                    battleService.cancelBattle(battleUuid);
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    if (Bukkit.getServer() != null) {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> beginActiveBattle(battleUuid, p1, p2, rules));
                    } else {
                        beginActiveBattle(battleUuid, p1, p2, rules);
                    }
                    return;
                }
                String titleText = ChatColor.YELLOW + "" + ChatColor.BOLD + remaining;
                String subtitle = messageService.component("queue.countdown_subtitle");
                float pitch = remaining <= 3 ? 1.4f : 1.0f;
                if (p1.isOnline()) {
                    p1.sendTitle(titleText, subtitle, 0, 18, 2);
                    p1.playSound(p1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, pitch);
                }
                if (p2.isOnline()) {
                    p2.sendTitle(titleText, subtitle, 0, 18, 2);
                    p2.playSound(p2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, pitch);
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginActiveBattle(UUID battleUuid, Player p1, Player p2, GameModeRules rules) {
        // Safety: if either player went offline between the last countdown tick and
        // the runTask dispatch, cancel the battle rather than starting a ghost duel.
        if (!p1.isOnline() || !p2.isOnline()) {
            Player online = p1.isOnline() ? p1 : (p2.isOnline() ? p2 : null);
            if (online != null) {
                messageService.send(online, "queue.opponent_disconnected");
            }
            battleService.cancelBattle(battleUuid);
            return;
        }
        PotionEffectType slowness = versionAdapter.getEffect("slowness");
        PotionEffectType jumpBoost = versionAdapter.getEffect("jump_boost");
        for (Player p : List.of(p1, p2)) {
            if (slowness != null) p.removePotionEffect(slowness);
            if (jumpBoost != null) p.removePotionEffect(jumpBoost);
        }
        if (!battleService.startBattle(battleUuid)) {
            // Start was cancelled by a PvPIndexBattleStartEvent listener.
            return;
        }
        BattleSession session = battleService.find(battleUuid).orElse(null);
        Object customMsgObj = session != null ? session.getMetadata().get("custom_start_message") : null;
        String customMsgStr = null;
        if (customMsgObj instanceof net.kyori.adventure.text.Component c) {
            customMsgStr = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(c);
        } else if (customMsgObj instanceof String s) {
            customMsgStr = s;
        }
        final String finalCustomMsg = customMsgStr;
        for (Player p : List.of(p1, p2)) {
            if (p.isOnline()) {
                p.sendTitle(messageService.component("queue.go_title"), "", 0, 16, 8);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.8f);
                if (finalCustomMsg != null) {
                    p.sendMessage(finalCustomMsg);
                } else {
                    messageService.send(p, "battle.started_good_luck");
                }
            }
        }
        battleService.scheduleTimeLimit(battleUuid, rules.timeLimitSeconds());
    }

    private void teleportToArena(Player p1, Player p2, ArenaInstance instance) {
        World world = Bukkit.getWorld(instance.worldName());
        if (world == null) {
            plugin.getLogger().warning("[Queue] Arena world '"
                    + instance.worldName() + "' is not loaded. skipping teleport.");
            return;
        }
        List<SpawnPoint> spawns = instance.spawnPoints();
        if (spawns.size() >= 1) {
            SpawnPoint sp1 = spawns.get(0);
            p1.teleport(new Location(world, sp1.x(), sp1.y(), sp1.z(), sp1.yaw(), sp1.pitch()));
        }
        if (spawns.size() >= 2) {
            SpawnPoint sp2 = spawns.get(1);
            p2.teleport(new Location(world, sp2.x(), sp2.y(), sp2.z(), sp2.yaw(), sp2.pitch()));
        }
    }
}
