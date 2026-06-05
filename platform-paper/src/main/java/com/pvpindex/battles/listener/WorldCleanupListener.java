package com.pvpindex.battles.listener;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.velocity.VelocityTracker;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Handles world and chunk lifecycle events to ensure no battle state or
 * velocity-tracking data is left dangling when an arena world is unloaded.
 *
 * <h3>WorldUnloadEvent</h3>
 * <p>If an arena world is unloaded while a battle is still active (e.g. a
 * server admin forcibly unloads it, or the arena pool misfire), every battle
 * whose arena is in that world is cancelled gracefully: player states are
 * restored, arenas are released, and a warning is logged.</p>
 *
 * <h3>ChunkUnloadEvent</h3>
 * <p>No actionable work is done here for now. chunk unloads within an arena
 * world are expected (e.g. after a world-copy strategy shrinks the view
 * distance). Debug-level logging is emitted so server owners can correlate
 * chunk unload activity with battle issues.</p>
 */
public class WorldCleanupListener implements Listener {

    private final Plugin plugin;
    private final BattleService battleService;
    private final VelocityTracker velocityTracker;

    public WorldCleanupListener(Plugin plugin, BattleService battleService, VelocityTracker velocityTracker) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.velocityTracker = velocityTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();

        List<UUID> affected = battleService.findBattleUuidsForWorld(worldName);
        if (affected.isEmpty()) return;

        plugin.getLogger().warning("[WorldCleanup] World '" + worldName + "' is unloading with "
                + affected.size() + " active battle(s). cancelling them to prevent ghost sessions.");

        for (UUID battleUuid : affected) {
            try {
                battleService.cancelBattle(battleUuid);
                if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
                    plugin.getLogger().fine("[WorldCleanup] Cancelled battle " + battleUuid
                            + " (arena world '" + worldName + "' unloaded).");
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[WorldCleanup] Failed to cancel battle "
                        + battleUuid + ": " + e.getMessage());
            }
        }

        // Clear any velocity data for players that were in the affected battles.
        if (velocityTracker != null) {
            battleService.activeBattles().stream()
                    .flatMap(s -> s.getParticipants().stream())
                    .map(p -> p.getUuid())
                    .forEach(velocityTracker::clear);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) return;
        String worldName = event.getWorld().getName();
        if (!worldName.startsWith("pvpindex_")) return;
        plugin.getLogger().fine("[WorldCleanup] Chunk (" + event.getChunk().getX() + ","
                + event.getChunk().getZ() + ") unloading in arena world '" + worldName + "'.");
    }
}
