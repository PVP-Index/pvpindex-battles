package com.pvpindex.battles.practice.bot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Static NMS reflection utilities shared by {@link BotNmsAdapter} implementations.
 *
 * <p>All methods are version-agnostic: they use only {@code java.lang.reflect} and
 * never import NMS classes at compile time, so they work on Paper/Spigot 1.21.x
 * and Paper 26.1.x without recompilation.</p>
 *
 * <p>This class is intentionally not exported as public API — it exists solely to
 * eliminate duplication between {@code BotNmsAdapter121} and {@code BotNmsAdapter2610}.</p>
 */
public final class NmsSpawnHelper {

    private NmsSpawnHelper() {}

    // ── ClientInformation ─────────────────────────────────────────────────────

    /**
     * Locates and invokes {@code ClientInformation.createDefault()} across the class
     * locations that differ between Paper 1.21.x and 26.1.x.
     *
     * @return the default {@code ClientInformation} instance, or {@code null} if none
     *         of the candidate class paths exist on the current server
     */
    public static Object resolveClientInformationDefault() {
        // Ordered by likelihood: 26.1.x path first, then 1.21.x fallbacks
        String[] candidates = {
            "net.minecraft.server.level.ClientInformation",
            "net.minecraft.server.network.ClientInformation",
            "net.minecraft.network.protocol.game.ClientboundLoginPacket$ClientInformation"
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

    // ── ServerPlayer constructor ──────────────────────────────────────────────

    /**
     * Finds the {@code ServerPlayer} constructor that accepts four parameters:
     * {@code (MinecraftServer, ServerLevel, GameProfile, ClientInformation)}.
     *
     * @param serverPlayerClass the resolved {@code net.minecraft.server.level.ServerPlayer} class
     * @return the accessible constructor
     * @throws NoSuchMethodException if no 4-parameter constructor exists
     */
    public static Constructor<?> resolveServerPlayerConstructor(Class<?> serverPlayerClass)
            throws NoSuchMethodException {
        for (Constructor<?> c : serverPlayerClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == 4) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new NoSuchMethodException("No 4-param constructor on ServerPlayer");
    }

    // ── ServerLevel.addFreshEntity ────────────────────────────────────────────

    /**
     * Reflectively calls {@code ServerLevel.addFreshEntity(Entity[, SpawnReason])}.
     * Tries the 1-parameter overload first; falls back to the 2-parameter variant
     * (Paper 1.21.4+ introduced an optional {@code SpawnReason} argument).
     *
     * @param nmsLevel the NMS {@code ServerLevel} object
     * @param bot      the NMS {@code ServerPlayer} (or any {@code Entity}) to add
     * @throws Exception if the method is not found or the invocation fails
     */
    public static void addFreshEntityReflective(Object nmsLevel, Object bot) throws Exception {
        // 1-param: addFreshEntity(Entity) — most versions
        for (Method m : nmsLevel.getClass().getMethods()) {
            if ("addFreshEntity".equals(m.getName()) && m.getParameterCount() == 1) {
                m.invoke(nmsLevel, bot);
                return;
            }
        }
        // 2-param: addFreshEntity(Entity, SpawnReason) — pass the first enum constant
        for (Method m : nmsLevel.getClass().getMethods()) {
            if ("addFreshEntity".equals(m.getName()) && m.getParameterCount() == 2) {
                Object defaultReason = m.getParameterTypes()[1].getEnumConstants()[0];
                m.invoke(nmsLevel, bot, defaultReason);
                return;
            }
        }
        throw new NoSuchMethodException(
                "addFreshEntity not found on " + nmsLevel.getClass().getSimpleName());
    }

    // ── NMS entity cleanup ────────────────────────────────────────────────────

    /**
     * Best-effort NMS entity removal for a fake player whose spawn failed partway.
     *
     * <p>Calls {@code ServerLevel.removePlayerImmediately(entity, RemovalReason.DISCARDED)},
     * which properly unregisters the entity from:</p>
     * <ul>
     *   <li>{@code ServerLevel} entity tracking / {@code EntityLookup}</li>
     *   <li>{@code ServerWaypointManager} (Paper 26.1.x) — prevents the NPE in
     *       {@code WaypointTransmitter$EntityChunkConnection.disconnect()} that fires
     *       when a real player teleports and the waypoint manager iterates receivers</li>
     *   <li>{@code PlayerList.players}</li>
     * </ul>
     *
     * <p>Falls back to {@code entity.setRemoved(DISCARDED)}, then {@code entity.discard()}.
     * All exceptions are swallowed — this is strictly best-effort cleanup.</p>
     *
     * @param nmsLevel the NMS {@code ServerLevel} the entity was (partially) added to;
     *                 may be {@code null} if the level was never resolved
     * @param entity   the NMS {@code Entity} / {@code ServerPlayer} to remove
     */
    public static void tryRemoveEntity(Object nmsLevel, Object entity) {
        // Resolve RemovalReason.DISCARDED (inner enum — binary name uses '$')
        Object discarded = null;
        try {
            Class<?> rrClass = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            for (Object c : rrClass.getEnumConstants()) {
                if ("DISCARDED".equals(c.toString())) { discarded = c; break; }
            }
        } catch (Exception ignored) {}

        // Preferred: ServerLevel.removePlayerImmediately(entity, DISCARDED)
        // Mirrors what Paper calls during cross-world teleport; cleans up all tracking.
        if (discarded != null && nmsLevel != null) {
            try {
                for (Method m : nmsLevel.getClass().getMethods()) {
                    if ("removePlayerImmediately".equals(m.getName()) && m.getParameterCount() == 2) {
                        m.invoke(nmsLevel, entity, discarded);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Fallback: entity.setRemoved(DISCARDED)
        if (discarded != null) {
            try {
                for (Method m : entity.getClass().getMethods()) {
                    if ("setRemoved".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.invoke(entity, discarded);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Last resort: entity.discard() (Paper convenience wrapper for setRemoved)
        try { entity.getClass().getMethod("discard").invoke(entity); } catch (Exception ignored) {}
    }
}
