package com.pvpindex.battles.listener;

import com.pvpindex.battles.command.BattleGuiCommand;
import com.pvpindex.battles.practice.PracticeManager;
import com.pvpindex.battles.practice.PracticeMode;
import com.pvpindex.battles.practice.reaction.ReactionTarget;
import com.pvpindex.battles.practice.reaction.ReactionTrainer;
import com.pvpindex.battles.practice.bot.BotSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles all events for the practice mode system:
 *
 * <ul>
 *   <li><b>Reaction hits</b>: when a player sword-swings and lands on a
 *       reaction-target {@link ArmorStand}, the hit is credited and the
 *       target bursts.</li>
 *   <li><b>Bot combat</b>: lets the real damage pipeline run; monitors the
 *       bot's health and calls {@link PracticeManager#onBotDefeated} when it
 *       reaches zero.</li>
 *   <li><b>Practice GUI</b>: handles clicks inside the practice-mode selection
 *       inventory (identified by {@link PracticeManager#GUI_TITLE}).</li>
 *   <li><b>Session teardown</b>: ends sessions on player death / quit.</li>
 * </ul>
 */
public final class PracticeListener implements Listener {

    // HP threshold at which we proactively end the bot duel to avoid NMS death crash
    private static final double BOT_DEATH_HP_THRESHOLD = 1.0;

    private final PracticeManager practiceManager;
    private final BattleGuiCommand battleGuiCommand;

    public PracticeListener(PracticeManager practiceManager, BattleGuiCommand battleGuiCommand) {
        this.practiceManager  = practiceManager;
        this.battleGuiCommand = battleGuiCommand;
    }

    // ── Damage events ─────────────────────────────────────────────────────────

    /**
     * Handles two cases:
     * <ol>
     *   <li>Player hits a reaction-target ArmorStand → credit hit + burst.</li>
     *   <li>Player hits the practice bot → let damage run normally, then check
     *       if the bot should be considered defeated.</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity target = event.getEntity();

        // ── Case 1: Reaction target hit ────────────────────────────────────
        if (target instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(ReactionTarget.PDC_KEY, PersistentDataType.BYTE)) {
                event.setCancelled(true); // don't break the armorstand; remove via ReactionTarget
                ReactionTrainer trainer = practiceManager.getTrainer(attacker.getUniqueId());
                if (trainer == null) return;
                // Find the matching ReactionTarget
                for (ReactionTarget rt : trainer.activeTargets()) {
                    if (rt.stand().getEntityId() == stand.getEntityId() && !rt.isRemoved()) {
                        trainer.onTargetHit(rt);
                        return;
                    }
                }
            }
            return;
        }

        // ── Case 2: Bot hit check ──────────────────────────────────────────
        BotSession bs = practiceManager.getBotSession(attacker.getUniqueId());
        if (bs == null || bs.isEnded()) return;
        if (bs.opponent().entity().getEntityId() != target.getEntityId()) return;

        // Let the normal damage run; after the event, check if HP is critical
        // We schedule a 1-tick delayed check so the damage has been applied
        try {
            org.bukkit.plugin.Plugin plugin = getPlugin(attacker);
            target.getServer().getScheduler().runTask(plugin, () -> {
                if (bs.isEnded()) return;
                double hp = bs.opponent().getHealth();
                if (hp <= BOT_DEATH_HP_THRESHOLD) {
                    practiceManager.onBotDefeated(attacker);
                }
            });
        } catch (IllegalStateException ignored) {
            // Plugin not found during shutdown — skip deferred check
        }
    }

    /** Catch the bot's actual EntityDeathEvent in case HP hits 0 before our threshold check. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        // Find which player owns a bot session with this entity
        for (var entry : iterateBotSessions()) {
            BotSession bs = entry.botSession();
            if (bs.isEnded()) continue;
            if (bs.opponent().entity().getEntityId() == event.getEntity().getEntityId()) {
                event.setDroppedExp(0);
                event.getDrops().clear();
                Player owner = event.getEntity().getServer().getPlayer(entry.ownerUuid());
                if (owner != null) {
                    practiceManager.onBotDefeated(owner);
                }
                return;
            }
        }
    }

    // ── Player events ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!practiceManager.isInPractice(player.getUniqueId())) return;
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        // End session; state will be restored
        player.getServer().getScheduler().runTask(
                getPlugin(player), () -> practiceManager.endSession(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (practiceManager.isInPractice(player.getUniqueId())) {
            practiceManager.endSession(player);
        }
    }

    // ── Practice GUI clicks ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // Practice mode selection GUI
        if (PracticeManager.GUI_TITLE.equals(title)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int slot = event.getRawSlot();
            switch (slot) {
                case 11 -> { // Reaction Training
                    player.closeInventory();
                    practiceManager.startSession(player, PracticeMode.REACTION_TRAINING, null);
                }
                case 13 -> { // Bot Duel
                    player.closeInventory();
                    practiceManager.startSession(player, PracticeMode.BOT_DUEL, null);
                }
                case 22 -> { // Back → open /battle GUI
                    player.closeInventory();
                    if (battleGuiCommand != null) {
                        battleGuiCommand.onCommand(player,
                                player.getServer().getPluginCommand("battle"),
                                "battle", new String[0]);
                    }
                }
                default -> { /* ignore filler clicks */ }
            }
            return;
        }

        // Practice tab click inside the battle GUI (slot 39)
        if (battleGuiCommand != null && battleGuiCommand.guiConfig().battleTitle().equals(title)) {
            if (event.getRawSlot() == 39) {
                event.setCancelled(true);
                player.closeInventory();
                practiceManager.openPracticeGui(player);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the owning plugin for a Bukkit player's server — used for
     * scheduling delayed tasks.  Falls back gracefully if plugin lookup fails.
     */
    private org.bukkit.plugin.Plugin getPlugin(Player player) {
        org.bukkit.plugin.Plugin p = player.getServer().getPluginManager().getPlugin("PvPIndexBattles");
        if (p != null) return p;
        throw new IllegalStateException("PvPIndexBattles plugin not found — cannot schedule task.");
    }

    /**
     * Snapshot of (ownerUuid, botSession) pairs for safe iteration in
     * EntityDeathEvent (avoids ConcurrentModificationException).
     */
    private Iterable<BotSessionEntry> iterateBotSessions() {
        var result = new java.util.ArrayList<BotSessionEntry>();
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            BotSession bs = practiceManager.getBotSession(online.getUniqueId());
            if (bs != null) result.add(new BotSessionEntry(online.getUniqueId(), bs));
        }
        return result;
    }

    private record BotSessionEntry(java.util.UUID ownerUuid, BotSession botSession) {}
}
