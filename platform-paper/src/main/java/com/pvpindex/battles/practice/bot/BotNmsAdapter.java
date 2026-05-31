package com.pvpindex.battles.practice.bot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/**
 * Version-specific NMS adapter for spawning a headless fake-player entity.
 *
 * <p>Implementations live in the version-specific {@code paper-versions} modules
 * and are loaded at start-up via {@code Class.forName} in the bootstrap plugin.
 * The currently registered adapter is injected into {@link BotPlayerFactory} via
 * {@link BotPlayerFactory#setNmsAdapter}.</p>
 *
 * <p>Platform support:</p>
 * <ul>
 *   <li><b>Paper 1.21.x</b> — {@code BotNmsAdapter121} (no ServerWaypointManager)</li>
 *   <li><b>Paper 26.1.x</b> — {@code BotNmsAdapter2610} (ServerWaypointManager cleanup)</li>
 *   <li><b>Spigot 1.21.x</b> — {@code BotNmsAdapter121} works (same NMS classes)</li>
 *   <li><b>Folia</b>         — no adapter loaded; Zombie fallback always used</li>
 * </ul>
 *
 * <p>Each implementation guarantees that any partial NMS state (entity tracking,
 * {@code PlayerList}, {@code ServerWaypointManager}) is cleaned up before returning
 * {@code null} or re-throwing an exception.</p>
 */
public interface BotNmsAdapter {

    /**
     * Attempts to spawn a headless NMS {@code ServerPlayer} at {@code location}.
     *
     * @param world    the target world
     * @param location spawn position
     * @param name     display name (≤ 16 chars for most NMS versions)
     * @return the Bukkit {@link LivingEntity} wrapping the fake player on success,
     *         or {@code null} if NMS spawning is not supported on this version
     * @throws Exception if a recoverable failure occurred; the implementation must
     *                   clean up all partial NMS state before re-throwing
     */
    LivingEntity spawnFakePlayer(World world, Location location, String name) throws Exception;
}
