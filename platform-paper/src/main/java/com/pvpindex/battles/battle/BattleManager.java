package com.pvpindex.battles.battle;

import com.pvpindex.battles.identifier.WorldIdentifier;
import com.pvpindex.battles.identifier.WorldNormalizer;
import com.pvpindex.battles.queue.BattleQueueService;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * High-level facade over {@link BattleService}, {@link BattleQueueService},
 * and {@link WorldNormalizer}. Used by commands, GUI, and placeholders to
 * avoid reaching into individual services directly.
 */
public final class BattleManager {

	private final BattleService battleService;
	private final BattleQueueService queueService;
	private final WorldNormalizer worldNormalizer;

	public BattleManager(BattleService battleService, BattleQueueService queueService,
			WorldNormalizer worldNormalizer) {
		this.battleService = battleService;
		this.queueService = queueService;
		this.worldNormalizer = worldNormalizer;
	}

	public Collection<BattleSession> activeBattles() {
		return battleService.activeBattles();
	}

	public boolean hasActiveBattle(UUID playerUuid) {
		return battleService.hasActiveBattle(playerUuid);
	}

	public Optional<BattleSession> findActiveBattleFor(UUID playerUuid) {
		return battleService.findActiveBattleFor(playerUuid);
	}

	public boolean isQueued(UUID playerUuid) {
		return queueService.isQueued(playerUuid);
	}

	public int queueSize(String rawModeId) {
		return queueService.queueSize(rawModeId);
	}

	public Set<UUID> queuedPlayers(String rawModeId) {
		return Set.of();
	}

	/**
	 * Resolves a mode input string (could be raw ID or display name) to a
	 * {@link WorldIdentifier}.
	 */
	public Optional<WorldIdentifier> resolveMode(String input) {
		return worldNormalizer.resolve(input);
	}

	public WorldNormalizer worldNormalizer() { return worldNormalizer; }
	public BattleService battleService() { return battleService; }
	public BattleQueueService queueService() { return queueService; }
}
