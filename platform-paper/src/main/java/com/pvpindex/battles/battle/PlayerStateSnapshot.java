package com.pvpindex.battles.battle;

import java.util.Collection;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Immutable capture of a player's pre-battle state. Restored after the
 * battle ends or when the player rejoins after a mid-battle disconnect.
 */
public record PlayerStateSnapshot(
        Location location,
        GameMode gameMode,
        double health,
        double maxHealth,
        int foodLevel,
        float saturation,
        float exhaustion,
        float exp,
        int level,
        int fireTicks,
        float fallDistance,
        boolean allowFlight,
        boolean isFlying,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offhand,
        ItemStack[] enderChest,
        Collection<PotionEffect> potionEffects
) {
    public static PlayerStateSnapshot capture(Player player, boolean includeEnderChest, org.bukkit.attribute.Attribute maxHealthAttr) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] enderContents = includeEnderChest
                ? cloneArray(player.getEnderChest().getContents())
                : null;
        return new PlayerStateSnapshot(
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getAttribute(maxHealthAttr) != null
                        ? player.getAttribute(maxHealthAttr).getBaseValue()
                        : 20.0d,
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getExp(),
                player.getLevel(),
                player.getFireTicks(),
                player.getFallDistance(),
                player.getAllowFlight(),
                player.isFlying(),
                cloneArray(inv.getStorageContents()),
                cloneArray(inv.getArmorContents()),
                inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone(),
                enderContents,
                List.copyOf(player.getActivePotionEffects())
        );
    }

    /** Apply this snapshot back onto the (possibly newly-rejoined) player. */
    public void restore(Player player, org.bukkit.attribute.Attribute maxHealthAttr) {
        restoreInternal(player, maxHealthAttr, true);
    }

    /**
     * Restore location, game mode, health, food, potions, and flight but
     * keep the player's current inventory as-is. Used after SMP battles
     * where the winner retains looted items.
     */
    public void restoreWithoutInventory(Player player, org.bukkit.attribute.Attribute maxHealthAttr) {
        restoreInternal(player, maxHealthAttr, false);
    }

    private void restoreInternal(Player player, org.bukkit.attribute.Attribute maxHealthAttr, boolean includeInventory) {
        // Clear hostile state first
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.setFireTicks(0);
        player.setFallDistance(0.0f);

        // Restore attributes
        var attr = player.getAttribute(maxHealthAttr);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(health, attr != null ? attr.getValue() : maxHealth));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setExp(exp);
        player.setLevel(level);
        player.setAllowFlight(allowFlight);
        player.setFlying(isFlying);
        player.setGameMode(gameMode);

        if (includeInventory) {
            PlayerInventory inv = player.getInventory();
            inv.clear();
            inv.setStorageContents(cloneArray(inventory));
            inv.setArmorContents(cloneArray(armor));
            if (offhand != null) inv.setItemInOffHand(offhand.clone());
            if (enderChest != null) {
                player.getEnderChest().setContents(cloneArray(enderChest));
            }
        }

        // Potion effects
        potionEffects.forEach(player::addPotionEffect);

        // Teleport last — some clients flicker if state is set after tp
        player.teleport(location);
    }

    private static ItemStack[] cloneArray(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }
}
