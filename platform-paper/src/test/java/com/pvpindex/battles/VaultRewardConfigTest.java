package com.pvpindex.battles;

import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.reward.VaultRewardConfig;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultRewardConfigTest {

	@Test
	void calculateMultiplier_streakDisabled_alwaysReturnsOne() {
		VaultRewardConfig config = new VaultRewardConfig(
				true, Map.of(), false, 0.5, 2.0, 2);
		assertEquals(1.0, config.calculateMultiplier(0));
		assertEquals(1.0, config.calculateMultiplier(1));
		assertEquals(1.0, config.calculateMultiplier(5));
		assertEquals(1.0, config.calculateMultiplier(100));
	}

	@Test
	void calculateMultiplier_belowMinStreak_returnsOne() {
		VaultRewardConfig config = new VaultRewardConfig(
				true, Map.of(), true, 0.5, 3.0, 3);
		assertEquals(1.0, config.calculateMultiplier(0));
		assertEquals(1.0, config.calculateMultiplier(1));
		assertEquals(1.0, config.calculateMultiplier(2));
	}

	@Test
	void calculateMultiplier_atAndAboveMinStreak_scales() {
		VaultRewardConfig config = new VaultRewardConfig(
				true, Map.of(), true, 0.5, 3.0, 2);
		// streak=2: 1.0 + (2-1)*0.5 = 1.5
		assertEquals(1.5, config.calculateMultiplier(2), 0.001);
		// streak=3: 1.0 + (3-1)*0.5 = 2.0
		assertEquals(2.0, config.calculateMultiplier(3), 0.001);
		// streak=4: 1.0 + (4-1)*0.5 = 2.5
		assertEquals(2.5, config.calculateMultiplier(4), 0.001);
	}

	@Test
	void calculateMultiplier_cappedAtMax() {
		VaultRewardConfig config = new VaultRewardConfig(
				true, Map.of(), true, 0.5, 2.0, 2);
		// streak=5: 1.0 + (5-1)*0.5 = 3.0, but capped at 2.0
		assertEquals(2.0, config.calculateMultiplier(5), 0.001);
		assertEquals(2.0, config.calculateMultiplier(100), 0.001);
	}

	@Test
	void baseReward_returnsMappedValue() {
		Map<GameModeType, Double> rewards = new EnumMap<>(GameModeType.class);
		rewards.put(GameModeType.SWORD, 500.0);
		rewards.put(GameModeType.CRYSTAL, 800.0);
		VaultRewardConfig config = new VaultRewardConfig(
				true, rewards, false, 0.5, 2.0, 2);

		assertEquals(500.0, config.baseReward(GameModeType.SWORD));
		assertEquals(800.0, config.baseReward(GameModeType.CRYSTAL));
	}

	@Test
	void baseReward_unmappedMode_returnsZero() {
		VaultRewardConfig config = new VaultRewardConfig(
				true, Map.of(), false, 0.5, 2.0, 2);
		assertEquals(0.0, config.baseReward(GameModeType.SWORD));
	}
}
