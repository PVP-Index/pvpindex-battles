package com.pvpindex.paper.v1_21;

import com.pvpindex.battles.version.VersionAdapter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

public final class Paper121VersionAdapter implements VersionAdapter {

	@Override
	public Attribute getMaxHealthAttribute() {
		return Attribute.GENERIC_MAX_HEALTH;
	}

	@Override
	public PotionEffectType getEffect(String minecraftKey) {
		return Registry.EFFECT.get(NamespacedKey.minecraft(minecraftKey));
	}

	@Override
	public Enchantment getEnchantment(String minecraftKey) {
		return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(minecraftKey));
	}
}
