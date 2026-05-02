package com.pvpindex.battles.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
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
			meta.displayName(Component.empty());
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
			NamedTextColor colour = active ? NamedTextColor.GREEN : NamedTextColor.GRAY;
			meta.displayName(Component.text(name, colour, TextDecoration.BOLD));
			if (active) {
				meta.lore(List.of(Component.text("Currently viewing", NamedTextColor.GREEN)));
				meta.setEnchantmentGlintOverride(true);
			}
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
			item.setItemMeta(meta);
		}
		return item;
	}
}
