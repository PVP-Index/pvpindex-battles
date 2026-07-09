package com.pvpindex.battles.gamemode;

import java.util.List;
import java.util.Map;

/**
 * Single inventory slot in a kit. {@code slot} accepts vanilla slot ids
 * (0..35 hotbar/main), or named slots: {@code helmet}, {@code chest},
 * {@code legs}, {@code boots}, {@code offhand}.
 */
public record KitItem(
        String slot,
        String material,
        int amount,
        Map<String, Integer> enchantments,
        List<String> lore,
        String displayName,
        List<String> potionEffects
) {
    public static KitItem of(String slot, String material) {
        return new KitItem(slot, material, 1, Map.of(), List.of(), null, List.of());
    }
}
