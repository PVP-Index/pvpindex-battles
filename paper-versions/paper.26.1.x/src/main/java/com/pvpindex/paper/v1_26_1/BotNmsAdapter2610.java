package com.pvpindex.paper.v1_26_1;

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
 * NMS bot adapter for <b>Paper 26.1.x</b> (Minecraft 1.21.4+).
 *
 * <p>Constructs a headless {@code net.minecraft.server.level.ServerPlayer} via
 * reflection.  Paper 26.1.x introduces {@code ServerWaypointManager}: when a
 * {@code ServerPlayer} is added to the level via {@code addFreshEntity} it is
 * registered as a waypoint <em>receiver</em>.  If the spawn subsequently fails
 * and the fake player retains a {@code null} connection, any future teleport of a
 * real player triggers {@code WaypointTransmitter$EntityChunkConnection.disconnect}
 * which calls {@code receiver.connection.send(packet)} and NPEs.</p>
 *
 * <p>The catch block therefore calls {@link NmsSpawnHelper#tryRemoveEntity}, which
 * uses {@code ServerLevel.removePlayerImmediately(entity, RemovalReason.DISCARDED)}
 * — the same cleanup path Paper itself uses for cross-world teleport — to fully
 * unregister the entity from {@code ServerWaypointManager}, {@code EntityLookup},
 * and {@code PlayerList} before re-throwing.</p>
 */
public final class BotNmsAdapter2610 implements BotNmsAdapter {

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
                        "ClientInformation.createDefault() not found on 26.1.x");
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
            // addFreshEntity also registers the fake player in ServerWaypointManager
            // as a receiver of nearby waypoints.  If this step succeeds and a later
            // step throws, tryRemoveEntity MUST be called (see catch block).
            NmsSpawnHelper.addFreshEntityReflective(nmsLevel, bot);

            // 8 ── Wrap as Bukkit entity ───────────────────────────────────────
            Object bukkit = bot.getClass().getMethod("getBukkitEntity").invoke(bot);
            if (bukkit instanceof LivingEntity le) return le;
            throw new IllegalStateException("getBukkitEntity() did not return a LivingEntity");

        } catch (Exception e) {
            // Critical on 26.1.x: removePlayerImmediately triggers the same
            // ServerLevel.EntityCallbacks.onTrackingEnd path as a normal cross-world
            // teleport, which calls ServerWaypointManager.removePlayer(fakeBotPlayer)
            // → removeReceiver(fakeBotPlayer), cleanly removing the fake bot from the
            // receiver map of every nearby waypoint (including the real player's).
            // Without this, the next real-player teleportAsync will NPE inside
            // WaypointTransmitter$EntityChunkConnection.disconnect().
            if (bot != null) {
                NmsSpawnHelper.tryRemoveEntity(nmsLevel, bot);
            }
            throw e;
        }
    }
}
