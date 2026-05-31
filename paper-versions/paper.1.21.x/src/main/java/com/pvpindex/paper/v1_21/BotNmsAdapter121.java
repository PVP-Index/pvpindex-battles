package com.pvpindex.paper.v1_21;

import com.pvpindex.battles.practice.bot.BotNmsAdapter;
import com.pvpindex.battles.practice.bot.NmsSpawnHelper;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/**
 * NMS bot adapter for <b>Paper / Spigot 1.21.x</b>.
 *
 * <p>Constructs a headless {@code net.minecraft.server.level.ServerPlayer} via
 * reflection and registers it with the world's entity tracking.  On 1.21.x
 * {@code ServerWaypointManager} does not exist, so the cleanup path only needs
 * to call {@code removePlayerImmediately} or {@code setRemoved(DISCARDED)} to
 * unregister the entity from {@code EntityLookup} and {@code PlayerList}.</p>
 *
 * <p>Also works on <b>Spigot 1.21.x</b>: Spigot shares the same NMS class paths
 * ({@code net.minecraft.server.level.*}) since the removal of versioned packages
 * in 1.17, so all reflection calls succeed identically.</p>
 */
public final class BotNmsAdapter121 implements BotNmsAdapter {

    @Override
    public LivingEntity spawnFakePlayer(World world, Location location, String name)
            throws Exception {
        Object nmsLevel = null;
        Object bot = null;
        try {
            // 1 ── MinecraftServer ─────────────────────────────────────────────
            Object craftServer = Bukkit.getServer();
            Object nmsServer = craftServer.getClass()
                    .getMethod("getServer")
                    .invoke(craftServer);

            // 2 ── ServerLevel ─────────────────────────────────────────────────
            nmsLevel = world.getClass()
                    .getMethod("getHandle")
                    .invoke(world);

            // 3 ── GameProfile ─────────────────────────────────────────────────
            UUID botUuid = UUID.nameUUIDFromBytes(
                    ("PracticeBot:" + name).getBytes(StandardCharsets.UTF_8));
            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gpClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(botUuid, name);

            // 4 ── ClientInformation.createDefault() ───────────────────────────
            Object clientInfo = NmsSpawnHelper.resolveClientInformationDefault();
            if (clientInfo == null) {
                throw new UnsupportedOperationException(
                        "ClientInformation.createDefault() not found on 1.21.x");
            }

            // 5 ── ServerPlayer constructor ────────────────────────────────────
            Class<?> spClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Constructor<?> ctor = NmsSpawnHelper.resolveServerPlayerConstructor(spClass);
            bot = ctor.newInstance(nmsServer, nmsLevel, profile, clientInfo);

            // 6 ── Set initial position ────────────────────────────────────────
            bot.getClass()
                    .getMethod("setPos", double.class, double.class, double.class)
                    .invoke(bot, location.getX(), location.getY(), location.getZ());

            // 7 ── Add to world ────────────────────────────────────────────────
            NmsSpawnHelper.addFreshEntityReflective(nmsLevel, bot);

            // 8 ── Wrap as Bukkit entity ───────────────────────────────────────
            Object bukkit = bot.getClass().getMethod("getBukkitEntity").invoke(bot);
            if (bukkit instanceof LivingEntity le) return le;
            throw new IllegalStateException("getBukkitEntity() did not return a LivingEntity");

        } catch (Exception e) {
            // Ensure the partially-registered entity is removed from EntityLookup
            // and PlayerList before re-throwing so the caller's Zombie fallback
            // does not race with a stale null-connection ServerPlayer.
            if (bot != null) {
                NmsSpawnHelper.tryRemoveEntity(nmsLevel, bot);
            }
            throw e;
        }
    }
}
