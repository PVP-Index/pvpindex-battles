package com.pvpindex.battles.gamemode;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies a {@link KitDefinition} to a {@link Player}'s inventory + effects.
 */
public final class KitApplier {

    private final com.pvpindex.battles.version.VersionAdapter versionAdapter;

    public KitApplier(com.pvpindex.battles.version.VersionAdapter versionAdapter) {
        this.versionAdapter = versionAdapter;
    }

    public KitApplier() {
        this.versionAdapter = null;
    }

    public void apply(Player player, KitDefinition kit) {
        var inv = player.getInventory();
        inv.clear();
        for (KitItem item : kit.items()) {
            ItemStack stack = build(item);
            if (stack == null) continue;
            placeIntoSlot(player, item.slot(), stack);
        }
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        for (String effectSpec : kit.potionEffects()) {
            applyEffect(player, effectSpec);
        }
    }

    private ItemStack build(KitItem item) {
        Material material = Material.matchMaterial(item.material());
        if (material == null) return null;
        ItemStack stack = new ItemStack(material, Math.max(1, item.amount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (item.displayName() != null) {
                meta.displayName(Component.text(item.displayName()));
            }
            if (!item.lore().isEmpty()) {
                meta.lore(item.lore().stream().map(Component::text).toList());
            }
            stack.setItemMeta(meta);
        }
        item.enchantments().forEach((name, level) -> {
            Enchantment enchant = versionAdapter != null
                    ? versionAdapter.getEnchantment(name.toLowerCase())
                    : org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase()));
            if (enchant != null) {
                stack.addUnsafeEnchantment(enchant, level);
            }
        });
        return stack;
    }

    private void placeIntoSlot(Player player, String slot, ItemStack stack) {
        var inv = player.getInventory();
        switch (slot.toLowerCase()) {
            case "helmet", "head" -> inv.setHelmet(stack);
            case "chest", "chestplate" -> inv.setChestplate(stack);
            case "legs", "leggings" -> inv.setLeggings(stack);
            case "feet", "boots" -> inv.setBoots(stack);
            case "offhand", "off" -> inv.setItemInOffHand(stack);
            default -> {
                try {
                    inv.setItem(Integer.parseInt(slot), stack);
                } catch (NumberFormatException ex) {
                    inv.addItem(stack);
                }
            }
        }
    }

    private void applyEffect(Player player, String spec) {
        // Format: TYPE:durationTicks:amplifier   (e.g. SPEED:600:1)
        String[] parts = spec.split(":");
        if (parts.length < 1) return;
        PotionEffectType type = versionAdapter != null
                ? versionAdapter.getEffect(parts[0].toLowerCase())
                : org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
        if (type == null) return;
        int duration = parts.length > 1 ? safeInt(parts[1], 600) : 600;
        int amplifier = parts.length > 2 ? safeInt(parts[2], 0) : 0;
        player.addPotionEffect(new PotionEffect(type, duration, amplifier));
    }

    private static int safeInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return fallback; }
    }
}
