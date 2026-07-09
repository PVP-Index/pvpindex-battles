package com.pvpindex.battles.listener.battle;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.GameModeRules;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Shared helpers for the split battle event listeners.
 */
public final class BattleListenerHelper {
    private BattleListenerHelper() {}

    public static boolean contains(BattleSession session, UUID uuid) {
        return session.getParticipants().stream().anyMatch(p -> p.getUuid().equals(uuid));
    }

    public static GameModeRules resolveRules(BattleSession session, GameModeRegistry registry) {
        if (registry != null) {
            Object modeIdObj = session.getMetadata().get("mode_id");
            if (modeIdObj instanceof String modeId) {
                return registry.findMode(modeId)
                        .map(GameModeDefinition::rules)
                        .orElse(GameModeRules.vanilla());
            }
        }
        return GameModeRules.vanilla();
    }

    /**
     * Merges {@code base} data with the actor's current position and look direction.
     * Coordinates are rounded to 1 decimal place to keep the replay compact.
     */
    public static Map<String, Object> withPos(Player actor, Map<String, Object> base) {
        Location loc = actor.getLocation();
        Map<String, Object> map = new LinkedHashMap<>(base);
        map.put("x", Math.round(loc.getX() * 10.0) / 10.0);
        map.put("y", Math.round(loc.getY() * 10.0) / 10.0);
        map.put("z", Math.round(loc.getZ() * 10.0) / 10.0);
        map.put("yaw", (double) (Math.round(loc.getYaw() * 10.0f) / 10.0f));
        map.put("pitch", (double) (Math.round(loc.getPitch() * 10.0f) / 10.0f));
        return map;
    }

    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static String classifyConsumable(Material material) {
        String name = material.name();
        if (name.contains("POTION")) return "potion";
        if (material.isEdible()) return "food";
        return "other";
    }

    public static boolean isPreBattleBlockedItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.ENDER_PEARL
                || type == Material.CHORUS_FRUIT
                || type == Material.CROSSBOW
                || type == Material.BOW
                || type == Material.FIRE_CHARGE
                || type == Material.WIND_CHARGE;
    }
}
