package com.pvpindex.battles.version;

import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Abstracts Paper API differences between Minecraft versions so that
 * the shared platform-paper code compiles against a single target but
 * runs correctly on any supported server.
 */
public interface VersionAdapter {

	Attribute getMaxHealthAttribute();

	PotionEffectType getEffect(String minecraftKey);

	Enchantment getEnchantment(String minecraftKey);
}
