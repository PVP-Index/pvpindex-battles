package com.pvpindex.battles.gui;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Static utilities for building inventory GUI layouts.
 */
public final class GUIBuilder {

	private GUIBuilder() {}

	/** Creates a glass pane with an empty display name (filler item). */
	public static ItemStack glassPane(Material glass) {
		ItemStack item = new ItemStack(glass);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(" ");
			item.setItemMeta(meta);
		}
		return item;
	}

	/** Fills an entire 9-slot row with the given item. */
	public static void fillRow(Inventory inv, int row, ItemStack item) {
		int start = row * 9;
		for (int i = start; i < start + 9; i++) {
			if (i < inv.getSize()) {
				inv.setItem(i, item.clone());
			}
		}
	}

	/** Builds a category tab item, glowing if it is the active tab. */
	public static ItemStack categoryItem(Material material, String name, boolean active) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			ChatColor colour = active ? ChatColor.GREEN : ChatColor.GRAY;
			meta.setDisplayName(colour + "" + ChatColor.BOLD + name);
			if (active) {
				meta.setLore(List.of(ChatColor.GREEN + "Currently viewing"));
			}
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
			item.setItemMeta(meta);
			if (active) {
				org.bukkit.enchantments.Enchantment glint =
						Registry.ENCHANTMENT.get(NamespacedKey.minecraft("luck_of_the_sea"));
				if (glint != null) item.addUnsafeEnchantment(glint, 1);
			}
		}
		return item;
	}
}
