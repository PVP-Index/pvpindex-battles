package com.pvpindex.paper.v1_26_1;

import com.pvpindex.battles.version.VersionAdapter;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

public final class Paper2610VersionAdapter implements VersionAdapter {

	@Override
	public Attribute getMaxHealthAttribute() {
		return Attribute.MAX_HEALTH;
	}

	@Override
	public PotionEffectType getEffect(String minecraftKey) {
		return RegistryAccess.registryAccess()
				.getRegistry(RegistryKey.MOB_EFFECT)
				.get(NamespacedKey.minecraft(minecraftKey));
	}

	@Override
	public Enchantment getEnchantment(String minecraftKey) {
		return RegistryAccess.registryAccess()
				.getRegistry(RegistryKey.ENCHANTMENT)
				.get(NamespacedKey.minecraft(minecraftKey));
	}
}
