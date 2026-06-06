package com.pvpindex.battles.reward;

import com.pvpindex.battles.battle.type.GameModeType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record VaultRewardConfig(
		boolean enabled,
		Map<GameModeType, Double> rewards,
		boolean streakEnabled,
		double streakIncrement,
		double streakMax,
		int streakMinStreak) {

	public static VaultRewardConfig load(YamlConfiguration cfg) {
		ConfigurationSection eco = cfg.getConfigurationSection("economy");
		if (eco == null) {
			return new VaultRewardConfig(false, Map.of(), false, 0.5, 2.0, 2);
		}

		boolean enabled = eco.getBoolean("enabled", false);

		Map<GameModeType, Double> rewards = new EnumMap<>(GameModeType.class);
		ConfigurationSection rewardSection = eco.getConfigurationSection("rewards");
		if (rewardSection != null) {
			for (String key : rewardSection.getKeys(false)) {
				try {
					GameModeType mode = GameModeType.valueOf(key.toUpperCase(Locale.ROOT));
					rewards.put(mode, rewardSection.getDouble(key, 0));
				} catch (IllegalArgumentException ignored) {
					// unknown mode in config, skip
				}
			}
		}

		boolean streakEnabled = eco.getBoolean("streak_multiplier.enabled", false);
		double streakIncrement = eco.getDouble("streak_multiplier.increment", 0.5);
		double streakMax = eco.getDouble("streak_multiplier.max", 2.0);
		int streakMinStreak = eco.getInt("streak_multiplier.min_streak", 2);

		return new VaultRewardConfig(enabled, rewards, streakEnabled, streakIncrement, streakMax, streakMinStreak);
	}

	public double baseReward(GameModeType mode) {
		return rewards.getOrDefault(mode, 0.0);
	}

	public double calculateMultiplier(int streak) {
		if (!streakEnabled || streak < streakMinStreak) {
			return 1.0;
		}
		return Math.min(1.0 + (streak - 1) * streakIncrement, streakMax);
	}
}
