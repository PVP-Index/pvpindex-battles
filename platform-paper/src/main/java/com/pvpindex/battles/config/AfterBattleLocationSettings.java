package com.pvpindex.battles.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Controls where players end up after a battle. Three modes are supported:
 * <ul>
 *   <li>{@code RESTORE} (default) teleports back to the pre-battle location</li>
 *   <li>{@code LOBBY} teleports to a fixed, configured location</li>
 *   <li>{@code WORLD_SPAWN} teleports to the main world's spawn point</li>
 * </ul>
 */
public record AfterBattleLocationSettings(
        Mode mode,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public enum Mode {
        RESTORE,
        LOBBY,
        WORLD_SPAWN
    }

    public static AfterBattleLocationSettings defaults() {
        return new AfterBattleLocationSettings(Mode.RESTORE, "world", 0, 64, 0, 0f, 0f);
    }

    /**
     * Resolves the after-battle {@link Location} for the given mode. Returns
     * {@code null} when the mode is {@link Mode#RESTORE}, signalling that the
     * snapshot's original location should be used.
     */
    public Location resolveLocation() {
        return switch (mode) {
            case RESTORE -> null;
            case WORLD_SPAWN -> {
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
                yield w == null ? null : w.getSpawnLocation();
            }
            case LOBBY -> {
                World w = Bukkit.getWorld(world);
                yield w == null ? null : new Location(w, x, y, z, yaw, pitch);
            }
        };
    }
}
