package com.pvpindex.battles.battle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Immutable snapshot of a player's equipment and vital stats at the moment a
 * battle starts — captured after kit application and countdown completion.
 *
 * <p>Slots 0–8 of the player's storage contents (the hotbar) are stored
 * separately from slots 9–35 (the main inventory) to make anti-cheat
 * comparisons and API display straightforward.
 *
 * <p>Used for:
 * <ul>
 *   <li>Anti-cheat: detect inventory changes between battle start and end.</li>
 *   <li>API payload: include starting equipment in the submitted battle data.</li>
 *   <li>Replay enhancement: record starting state alongside replay frames.</li>
 * </ul>
 */
public record BattleStartSetup(
        UUID playerUuid,
        double health,
        double maxHealth,
        int foodLevel,
        ItemStack[] hotbar,
        ItemStack[] mainInventory,
        ItemStack[] armor,
        ItemStack offhand,
        List<PotionEffect> potionEffects,
        Instant capturedAt
) {
    /**
     * Capture the player's current state.
     *
     * @param player        the player to snapshot
     * @param maxHealthAttr the max-health attribute resolved by the version adapter
     */
    public static BattleStartSetup capture(Player player, Attribute maxHealthAttr) {
        PlayerInventory inv = player.getInventory();
        // getStorageContents() returns 36 slots: indices 0-8 hotbar, 9-35 main inventory.
        ItemStack[] storage = inv.getStorageContents();

        double maxHp = 20.0;
        var attr = player.getAttribute(maxHealthAttr);
        if (attr != null) maxHp = attr.getBaseValue();

        return new BattleStartSetup(
                player.getUniqueId(),
                player.getHealth(),
                maxHp,
                player.getFoodLevel(),
                cloneRange(storage, 0, 9),
                cloneRange(storage, 9, storage.length),
                cloneArray(inv.getArmorContents()),
                inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone(),
                List.copyOf(player.getActivePotionEffects()),
                Instant.now()
        );
    }

    private static ItemStack[] cloneRange(ItemStack[] src, int from, int to) {
        int len = Math.min(to, src.length) - from;
        if (len <= 0) return new ItemStack[0];
        ItemStack[] out = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            ItemStack item = src[from + i];
            out[i] = item == null ? null : item.clone();
        }
        return out;
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
