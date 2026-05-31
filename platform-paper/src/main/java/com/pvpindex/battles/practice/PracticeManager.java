package com.pvpindex.battles.practice;

import com.pvpindex.battles.arena.SpawnPoint;
import com.pvpindex.battles.battle.PlayerStateService;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.KitApplier;
import com.pvpindex.battles.gamemode.KitDefinition;
import com.pvpindex.battles.practice.bot.BotSession;
import com.pvpindex.battles.practice.reaction.ReactionTrainer;
import com.pvpindex.battles.world.ArenaInstance;
import com.pvpindex.battles.world.ArenaPoolService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Central service for the practice system.
 *
 * <p>Manages the per-player {@link PracticeSession} lifecycle, delegates to
 * {@link ReactionTrainer} and {@link BotSession} for the two sub-modes, and
 * owns the practice-mode selection GUI.</p>
 */
public final class PracticeManager {

    /** Inventory title used both to build and to identify the practice GUI. */
    public static final String GUI_TITLE = "\u00A76\u00A7lPractice Mode";

    private final Plugin plugin;
    private final PlayerStateService playerStateService;
    private final KitApplier kitApplier;
    private final GameModeRegistry gameModeRegistry;
    private final PracticeSettings settings;
    private final ArenaPoolService arenaPool;

    private final Map<UUID, PracticeSession> sessions    = new ConcurrentHashMap<>();
    private final Map<UUID, ReactionTrainer> trainers    = new ConcurrentHashMap<>();
    private final Map<UUID, BotSession>       botSessions = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaInstance>   practiceArenas = new ConcurrentHashMap<>();

    public PracticeManager(Plugin plugin,
            PlayerStateService playerStateService,
            KitApplier kitApplier,
            GameModeRegistry gameModeRegistry,
            PracticeSettings settings,
            ArenaPoolService arenaPool) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
        this.kitApplier = kitApplier;
        this.gameModeRegistry = gameModeRegistry;
        this.settings = settings;
        this.arenaPool = arenaPool;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Enter practice mode for {@code player}.
     *
     * @param player     the player
     * @param mode       which sub-mode to start
     * @param gameModeId optional game-mode kit override; {@code null} uses
     *                   {@link PracticeSettings#botKitId()}
     */
    public void startSession(Player player, PracticeMode mode, String gameModeId) {
        if (!settings.enabled()) {
            player.sendMessage(Component.text("Practice mode is not enabled on this server.", NamedTextColor.RED));
            return;
        }
        if (isInPractice(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a practice session. Use /practice stop to exit.",
                    NamedTextColor.YELLOW));
            return;
        }

        playerStateService.save(player);

        PracticeSession session = new PracticeSession(player.getUniqueId(), mode,
                gameModeId != null ? gameModeId : settings.botKitId());
        sessions.put(player.getUniqueId(), session);

        // When the arena pool is cold, world generation can take ~300 ms on the
        // main thread. Schedule the heavy acquire + teleport to the very next
        // tick so this command handler returns immediately. For a warm pool the
        // 1-tick deferral is imperceptible.
        String templateId = settings.practiceTemplateId();
        boolean poolCold = arenaPool != null
                && templateId != null && !templateId.isBlank()
                && arenaPool.poolSize(templateId) == 0;
        if (poolCold) {
            player.sendMessage(Component.text("Generating practice arena, one moment...", NamedTextColor.YELLOW));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ArenaInstance arena = acquireAndTeleport(player);
            launchMode(player, session, mode, gameModeId, arena);
        });
    }

    /** Start the appropriate sub-mode after the arena has been acquired and the player teleported. */
    private void launchMode(Player player, PracticeSession session, PracticeMode mode,
            String gameModeId, ArenaInstance arena) {
        switch (mode) {
            case REACTION_TRAINING -> startReactionTraining(player, session);
            case BOT_DUEL          -> {
                // Determine bot spawn: arena spawn[1] when available, else 4 blocks ahead
                Location botLoc = null;
                if (arena != null) {
                    List<SpawnPoint> spawns = arena.spawnPoints();
                    if (spawns != null && spawns.size() >= 2) {
                        SpawnPoint s1 = spawns.get(1);
                        World world = Bukkit.getWorld(arena.worldName());
                        if (world != null) {
                            botLoc = new Location(world, s1.x(), s1.y(), s1.z(), s1.yaw(), s1.pitch());
                        }
                    }
                }
                startBotDuel(player, session, botLoc);
            }
        }
    }

    /** End and clean up the practice session for {@code player}. */
    public void endSession(Player player) {
        PracticeSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        switch (session.mode()) {
            case REACTION_TRAINING -> {
                ReactionTrainer trainer = trainers.remove(player.getUniqueId());
                if (trainer != null) trainer.stop();
            }
            case BOT_DUEL -> {
                BotSession bs = botSessions.remove(player.getUniqueId());
                if (bs != null) bs.end(false);
            }
        }

        releaseArena(player.getUniqueId());

        if (player.isOnline()) {
            playerStateService.restore(player);
            player.sendMessage(Component.text("Practice session ended.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Called from {@link com.pvpindex.battles.listener.PracticeListener} when
     * the bot is defeated.  Ends the session, credits the player.
     */
    public void onBotDefeated(Player player) {
        BotSession bs = botSessions.remove(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        if (bs != null) bs.end(true); // shows "Bot Defeated!" title
        releaseArena(player.getUniqueId());
        if (player.isOnline()) {
            playerStateService.restore(player);
        }
    }

    // ── Practice GUI ──────────────────────────────────────────────────────────

    /**
     * Open the practice-mode selection inventory for {@code player}.
     * Clicks are handled by
     * {@link com.pvpindex.battles.listener.PracticeListener}.
     */
    public void openPracticeGui(Player player) {
        if (!settings.enabled()) {
            player.sendMessage(Component.text("Practice mode is not enabled on this server.", NamedTextColor.RED));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Slot 11 — Reaction Training
        inv.setItem(11, buildGuiItem(Material.CLOCK,
                "\u00A7b\u00A7lReaction Training",
                "\u00A77Hit the glowing targets as fast as you can!",
                "\u00A7aClick to start"));

        // Slot 13 — Bot Duel
        inv.setItem(13, buildGuiItem(Material.SKELETON_SKULL,
                "\u00A76\u00A7lBot Duel",
                "\u00A77Fight a mid-level practice bot.",
                "\u00A7aClick to start"));

        // Slot 22 — Back
        inv.setItem(22, buildGuiItem(Material.ARROW,
                "\u00A77\u2190 Back",
                "\u00A77Return to the Battle menu."));

        player.openInventory(inv);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isInPractice(UUID playerUuid)       { return sessions.containsKey(playerUuid); }
    public PracticeSession getSession(UUID playerUuid) { return sessions.get(playerUuid); }
    public ReactionTrainer getTrainer(UUID playerUuid) { return trainers.get(playerUuid); }
    public BotSession getBotSession(UUID playerUuid)   { return botSessions.get(playerUuid); }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void startReactionTraining(Player player, PracticeSession session) {
        player.sendMessage(Component.text(
                "Reaction training started! Hit the glowing targets as fast as you can.",
                NamedTextColor.GREEN));
        ReactionTrainer trainer = new ReactionTrainer(plugin, settings);

        // Pass the arena floor centre so targets spawn within arena bounds
        ArenaInstance arena = practiceArenas.get(player.getUniqueId());
        if (arena != null) {
            World world = Bukkit.getWorld(arena.worldName());
            List<SpawnPoint> spawns = arena.spawnPoints();
            if (world != null && spawns != null && !spawns.isEmpty()) {
                SpawnPoint s0 = spawns.get(0);
                if (spawns.size() >= 2) {
                    SpawnPoint s1 = spawns.get(1);
                    trainer.setArenaCenter(new Location(world,
                            (s0.x() + s1.x()) / 2.0, s0.y(),
                            (s0.z() + s1.z()) / 2.0));
                } else {
                    trainer.setArenaCenter(new Location(world, s0.x(), s0.y(), s0.z()));
                }
            }
        }

        trainers.put(player.getUniqueId(), trainer);
        trainer.start(player, session);
    }

    private void startBotDuel(Player player, PracticeSession session, Location botSpawn) {
        String kitId = session.gameModeId() != null ? session.gameModeId() : settings.botKitId();
        KitDefinition kit = gameModeRegistry.findKit(kitId)
                .orElseGet(() -> {
                    // fallback: look for any kit
                    var kits = gameModeRegistry.allKits();
                    return kits.isEmpty() ? null : kits.iterator().next();
                });
        if (kit == null) {
            player.sendMessage(Component.text("No kit found for practice. Configure gamemodes.yml.", NamedTextColor.RED));
            sessions.remove(player.getUniqueId());
            releaseArena(player.getUniqueId());
            playerStateService.restore(player);
            return;
        }

        // Apply kit to the player too
        kitApplier.apply(player, kit);

        // Bot spawn: use arena spawn[1] if available, otherwise 4 blocks ahead
        Location loc;
        if (botSpawn != null) {
            loc = botSpawn;
        } else {
            loc = player.getLocation().clone().add(
                    player.getLocation().getDirection().setY(0).normalize().multiply(4));
            loc.setYaw(player.getLocation().getYaw() + 180f);
        }

        player.sendMessage(Component.text("Bot duel started! Defeat the practice bot.", NamedTextColor.GREEN));

        BotSession bs = BotSession.spawn(plugin, player, loc, kit, kitApplier, settings);
        botSessions.put(player.getUniqueId(), bs);
    }

    /**
     * Acquire a practice arena from the pool and teleport {@code player} to
     * spawn[0].  Stores the {@link ArenaInstance} in {@code practiceArenas}.
     *
     * @return the acquired instance, or {@code null} if none was available
     */
    private ArenaInstance acquireAndTeleport(Player player) {
        String templateId = settings.practiceTemplateId();
        if (templateId == null || templateId.isBlank() || arenaPool == null) return null;
        try {
            ArenaInstance arena = arenaPool.acquire(templateId).orElse(null);
            if (arena == null) return null;
            practiceArenas.put(player.getUniqueId(), arena);
            List<SpawnPoint> spawns = arena.spawnPoints();
            if (spawns != null && !spawns.isEmpty()) {
                SpawnPoint s0 = spawns.get(0);
                World world = Bukkit.getWorld(arena.worldName());
                if (world != null) {
                    // teleportAsync uses Paper's async chunk system to pre-load
                    // destination chunks before completing the cross-world move.
                    player.teleportAsync(new Location(world, s0.x(), s0.y(), s0.z(), s0.yaw(), s0.pitch()));
                }
            }
            return arena;
        } catch (Exception e) {
            plugin.getLogger().warning("[Practice] Could not acquire arena '" + templateId + "': " + e.getMessage());
            return null;
        }
    }

    /** Release the arena held by {@code uuid} back to the pool. */
    private void releaseArena(UUID uuid) {
        ArenaInstance arena = practiceArenas.remove(uuid);
        if (arena != null && arenaPool != null) {
            arenaPool.release(arena);
        }
    }

    private static ItemStack buildGuiItem(Material material, String name, String... loreParts) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(loreParts));
            item.setItemMeta(meta);
        }
        return item;
    }
}
