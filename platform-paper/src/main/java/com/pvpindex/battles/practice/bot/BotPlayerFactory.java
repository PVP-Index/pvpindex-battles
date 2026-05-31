package com.pvpindex.battles.practice.bot;

import java.lang.reflect.Field;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

/**
 * Creates the bot entity for a {@link BotSession}.
 *
 * <p>NMS-based fake-player spawning is delegated to a {@link BotNmsAdapter} that
 * is registered once at start-up via {@link #setNmsAdapter}.  The correct adapter
 * is chosen by the bootstrap based on the detected Minecraft version:</p>
 * <ul>
 *   <li><b>Paper / Spigot 1.21.x</b> → {@code BotNmsAdapter121}</li>
 *   <li><b>Paper 26.1.x</b>          → {@code BotNmsAdapter2610}</li>
 *   <li><b>Folia (any version)</b>   → no adapter; Zombie fallback always used</li>
 * </ul>
 *
 * <p>If no adapter is available, or if the NMS spawn fails for any reason, a
 * {@link Zombie} entity is used as a functionally equivalent fallback.</p>
 */
public final class BotPlayerFactory {

    /**
     * Platform-specific NMS adapter injected by the bootstrap plugin.
     * {@code null} when NMS fake-players are unsupported (Folia) or when
     * {@code botUseNms} is disabled in {@code practice.yml}.
     */
    private static volatile BotNmsAdapter nmsAdapter = null;

    private BotPlayerFactory() {}

    /**
     * Registers the platform-specific NMS adapter.
     * Must be called from the main thread before any bot session is started.
     *
     * @param adapter the adapter to use, or {@code null} to disable NMS spawning
     */
    public static void setNmsAdapter(BotNmsAdapter adapter) {
        nmsAdapter = adapter;
    }

    /**
     * Spawn the bot entity at {@code location}.
     *
     * @param plugin   owning plugin (for logging)
     * @param world    target world
     * @param location spawn location
     * @param name     display name shown above the entity
     * @param useNms   if {@code true} and an adapter is installed, attempt NMS fake-player first
     * @return a {@link LivingEntity} (either a fake {@link org.bukkit.entity.Player} or a
     *         {@link Zombie} fallback) that is already added to the world
     */
    public static LivingEntity spawn(Plugin plugin, World world, Location location,
            String name, boolean useNms) {
        if (useNms) {
            BotNmsAdapter adapter = nmsAdapter; // volatile read — safe without sync
            if (adapter != null) {
                try {
                    LivingEntity fakePlayer = adapter.spawnFakePlayer(world, location, name);
                    if (fakePlayer != null) {
                        plugin.getLogger().info(
                                "[PracticeBot] Spawned NMS fake-player bot '" + name + "'.");
                        return fakePlayer;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning(
                            "[PracticeBot] NMS fake-player spawn failed ("
                            + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "). Falling back to Zombie bot.");
                    // Secondary safety net: the adapter already called tryRemoveEntity,
                    // but purge any lingering null-connection entry from PlayerList to
                    // guard against PlayerList.broadcastAll() NPE on the next server tick.
                    tryPurgeNullConnectionPlayers(plugin.getLogger());
                }
            } else {
                plugin.getLogger().fine(
                        "[PracticeBot] NMS bot requested but no adapter is available "
                        + "(Folia or unsupported version). Using Zombie fallback.");
            }
        }
        return spawnZombieFallback(world, location, name);
    }

    // ── Secondary safety net ──────────────────────────────────────────────────

    /**
     * Reflectively removes any {@code ServerPlayer} with a {@code null} connection
     * from {@code PlayerList.players}.
     *
     * <p>This is a secondary guard against {@code PlayerList.broadcastAll()} NPE
     * in case the adapter's {@link NmsSpawnHelper#tryRemoveEntity} cleanup was
     * incomplete.  Uses {@code removeIf()} which is safe on
     * {@code CopyOnWriteArrayList} (unlike {@code Iterator.remove()}).</p>
     */
    private static void tryPurgeNullConnectionPlayers(Logger logger) {
        try {
            Object craftServer = Bukkit.getServer();
            Object nmsServer   = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object playerList  = nmsServer.getClass().getMethod("getPlayerList").invoke(nmsServer);

            Field playersField = null;
            for (Class<?> c = playerList.getClass(); c != null && playersField == null;
                    c = c.getSuperclass()) {
                try { playersField = c.getDeclaredField("players"); }
                catch (NoSuchFieldException ignored) {}
            }
            if (playersField == null) return;
            playersField.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.List<Object> players =
                    (java.util.List<Object>) playersField.get(playerList);
            int before = players.size();
            players.removeIf(p -> p == null || hasNullConnection(p));
            int removed = before - players.size();
            if (removed > 0) {
                logger.warning("[PracticeBot] Purged " + removed
                        + " null-connection player(s) from PlayerList to prevent NPE crash.");
            }
        } catch (Exception ex) {
            logger.warning("[PracticeBot] Could not purge null-connection players: "
                    + ex.getMessage());
        }
    }

    private static boolean hasNullConnection(Object serverPlayer) {
        try {
            Field connField = null;
            for (Class<?> c = serverPlayer.getClass(); c != null && connField == null;
                    c = c.getSuperclass()) {
                try { connField = c.getDeclaredField("connection"); }
                catch (NoSuchFieldException ignored) {}
            }
            if (connField == null) return false;
            connField.setAccessible(true);
            return connField.get(serverPlayer) == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ── Zombie fallback ───────────────────────────────────────────────────────

    private static LivingEntity spawnZombieFallback(World world, Location location, String name) {
        Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
        zombie.setAI(false); // manual AI via BotBehaviorTask
        zombie.customName(net.kyori.adventure.text.Component.text(name,
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        zombie.setCustomNameVisible(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setBaby(false);
        return zombie;
    }
}
