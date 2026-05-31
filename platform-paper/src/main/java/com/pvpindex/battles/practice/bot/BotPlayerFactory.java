package com.pvpindex.battles.practice.bot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;

/**
 * Creates the bot entity for a {@link BotSession}.
 *
 * <p>When {@code useNms=true}, an NMS {@code ServerPlayer} is constructed via
 * reflection (works on Paper 1.20.5 + / 26.1 where CraftBukkit dropped the
 * versioned package prefix).  The returned Bukkit {@link Player} is a fully
 * valid entity in the world — it is visible to clients, equippable, and
 * damageable — but it has no network connection, so the server never tries to
 * send it movement confirmations.  All position updates are driven by
 * {@link BotBehaviorTask} using the {@code Entity.setPos()} NMS method.</p>
 *
 * <p>If NMS reflection fails for any reason the factory falls back to a
 * {@link Zombie} entity with custom equipment, which behaves identically from
 * the combat perspective while looking like a geared opponent.</p>
 */
public final class BotPlayerFactory {

    private BotPlayerFactory() {}

    /**
     * Spawn the bot entity at {@code location}.
     *
     * @param plugin   owning plugin (for logging)
     * @param world    target world
     * @param location spawn location
     * @param name     display name shown above the entity
     * @param useNms   if {@code true} attempt NMS fake-player first
     * @return a {@link LivingEntity} (either a fake {@link Player} or a
     *         {@link Zombie} fallback) that is already added to the world
     */
    public static LivingEntity spawn(Plugin plugin, World world, Location location,
            String name, boolean useNms) {
        if (useNms) {
            try {
                LivingEntity fakePlayer = spawnViaNms(world, location, name);
                if (fakePlayer != null) {
                    plugin.getLogger().info("[PracticeBot] Spawned NMS fake-player bot '" + name + "'.");
                    return fakePlayer;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[PracticeBot] NMS fake-player spawn failed ("
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "). Falling back to Zombie bot.");
                // The ServerPlayer ctor may partially register itself in PlayerList before
                // throwing, leaving a null-connection entry that causes an NPE inside
                // PlayerList.broadcastAll() on the very next server tick.  Remove it now.
                tryPurgeNullConnectionPlayers(plugin.getLogger());
            }
        }
        return spawnZombieFallback(world, location, name);
    }

    // ── NMS ServerPlayer ─────────────────────────────────────────────────────

    /**
     * Uses reflection to construct a headless {@code net.minecraft.server.level.ServerPlayer}
     * and add it to the world's entity list.  Tested on Paper 1.21.x and 26.1.x
     * (post-package-rename era where CraftBukkit lives in
     * {@code org.bukkit.craftbukkit.*} without a version suffix).
     */
    private static LivingEntity spawnViaNms(World world, Location location, String name)
            throws Exception {

        // 1 ── MinecraftServer ────────────────────────────────────────────
        Object craftServer = Bukkit.getServer();
        Object nmsServer = craftServer.getClass()
                .getMethod("getServer")
                .invoke(craftServer);

        // 2 ── ServerLevel ────────────────────────────────────────────────
        Object nmsLevel = world.getClass()
                .getMethod("getHandle")
                .invoke(world);

        // 3 ── GameProfile (com.mojang.authlib bundled at runtime with Paper) ─
        UUID botUuid = UUID.nameUUIDFromBytes(
                ("PracticeBot:" + name).getBytes(StandardCharsets.UTF_8));
        // Use reflection so we don't need a compile-time dependency on authlib
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = gameProfileClass
                .getConstructor(UUID.class, String.class)
                .newInstance(botUuid, name);

        // 4 ── ClientInformation.createDefault() ─────────────────────────
        Object clientInfo = resolveClientInformationDefault();
        if (clientInfo == null) {
            throw new UnsupportedOperationException("ClientInformation.createDefault() not found");
        }

        // 5 ── ServerPlayer constructor ───────────────────────────────────
        Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Constructor<?> ctor = resolveServerPlayerConstructor(serverPlayerClass,
                nmsServer, nmsLevel, clientInfo);
        Object bot = ctor.newInstance(nmsServer, nmsLevel, profile, clientInfo);

        // 6 ── Set initial position ───────────────────────────────────────
        bot.getClass()
                .getMethod("setPos", double.class, double.class, double.class)
                .invoke(bot, location.getX(), location.getY(), location.getZ());

        // 7 ── Add to world (triggers entity tracker → sends spawn packet) ─
        addFreshEntityReflective(nmsLevel, bot);

        // 8 ── Return as Bukkit entity ────────────────────────────────────
        Object bukkitEntity = bot.getClass().getMethod("getBukkitEntity").invoke(bot);
        if (bukkitEntity instanceof LivingEntity le) {
            return le;
        }
        throw new IllegalStateException("getBukkitEntity() did not return a LivingEntity");
    }

    /**
     * Searches for {@code ClientInformation.createDefault()} across the known
     * class locations that differ between Paper 1.21.x and 26.1.x.
     */
    private static Object resolveClientInformationDefault() {
        String[] candidates = {
            "net.minecraft.server.level.ClientInformation",
            "net.minecraft.network.protocol.game.ClientboundLoginPacket$ClientInformation",
            "net.minecraft.server.network.ClientInformation"
        };
        for (String className : candidates) {
            try {
                Class<?> cls = Class.forName(className);
                Method m = cls.getDeclaredMethod("createDefault");
                m.setAccessible(true);
                Object result = m.invoke(null);
                if (result != null) return result;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Finds the {@code ServerPlayer} constructor that accepts
     * (MinecraftServer, ServerLevel, GameProfile, ClientInformation).
     */
    private static Constructor<?> resolveServerPlayerConstructor(
            Class<?> serverPlayerClass, Object nmsServer, Object nmsLevel, Object clientInfo)
            throws NoSuchMethodException {
        // Walk all public constructors and pick the 4-param one
        for (Constructor<?> c : serverPlayerClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == 4) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new NoSuchMethodException("No 4-param constructor found on ServerPlayer");
    }

    /**
     * Reflectively calls {@code ServerLevel.addFreshEntity(Entity)} (Paper 1.21+)
     * or the equivalent method on the supplied {@code nmsLevel}.
     */
    private static void addFreshEntityReflective(Object nmsLevel, Object bot) throws Exception {
        // addFreshEntity(Entity, SpawnReason?) - try 1-param version first
        for (Method m : nmsLevel.getClass().getMethods()) {
            if (m.getName().equals("addFreshEntity") && m.getParameterCount() == 1) {
                m.invoke(nmsLevel, bot);
                return;
            }
        }
        // 2-param version with SpawnReason
        for (Method m : nmsLevel.getClass().getMethods()) {
            if (m.getName().equals("addFreshEntity") && m.getParameterCount() == 2) {
                // second param is SpawnReason enum – pass the DEFAULT value
                Object defaultReason = m.getParameterTypes()[1].getEnumConstants()[0];
                m.invoke(nmsLevel, bot, defaultReason);
                return;
            }
        }
        throw new NoSuchMethodException("addFreshEntity not found on " + nmsLevel.getClass().getSimpleName());
    }

    // ── NMS cleanup ───────────────────────────────────────────────────────────

    /**
     * Reflectively removes any {@code ServerPlayer} with a null {@code connection}
     * field from the server's {@code PlayerList.players} list.
     *
     * <p>This guards against a crash in {@code PlayerList.broadcastAll()} that
     * occurs when the {@code ServerPlayer} constructor partially registers the
     * entity before throwing, leaving a disconnected entry behind.</p>
     */
    private static void tryPurgeNullConnectionPlayers(Logger logger) {
        try {
            Object craftServer = Bukkit.getServer();
            Object nmsServer   = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object playerList  = nmsServer.getClass().getMethod("getPlayerList").invoke(nmsServer);

            // Walk up the class hierarchy to find the "players" List field
            Field playersField = null;
            for (Class<?> c = playerList.getClass(); c != null && playersField == null; c = c.getSuperclass()) {
                try { playersField = c.getDeclaredField("players"); } catch (NoSuchFieldException ignored) {}
            }
            if (playersField == null) return;
            playersField.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.List<Object> players = (java.util.List<Object>) playersField.get(playerList);
            int removed = 0;
            Iterator<Object> it = players.iterator();
            while (it.hasNext()) {
                Object p = it.next();
                if (p == null || hasNullConnection(p)) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                logger.warning("[PracticeBot] Purged " + removed
                        + " null-connection player(s) from PlayerList to prevent NPE crash.");
            }
        } catch (Exception ex) {
            logger.warning("[PracticeBot] Could not purge null-connection players: " + ex.getMessage());
        }
    }

    private static boolean hasNullConnection(Object serverPlayer) {
        try {
            Field connField = null;
            for (Class<?> c = serverPlayer.getClass(); c != null && connField == null; c = c.getSuperclass()) {
                try { connField = c.getDeclaredField("connection"); } catch (NoSuchFieldException ignored) {}
            }
            if (connField == null) return false;
            connField.setAccessible(true);
            return connField.get(serverPlayer) == null;
        } catch (Exception ex) {
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
