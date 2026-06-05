package com.pvpindex.battles.identifier;

/**
 * Maps a game-mode's internal raw ID to a human-friendly normalised name.
 * Used exclusively for UX (GUI, placeholders, commands, Velocity messages).
 * never injected into API payloads.
 *
 * @param rawId        machine identifier (e.g. {@code "sword"})
 * @param normalizedId display name (e.g. {@code "Sword Duel"})
 */
public record WorldIdentifier(String rawId, String normalizedId) {

	public WorldIdentifier {
		if (rawId == null || rawId.isBlank()) throw new IllegalArgumentException("rawId must not be blank");
		if (normalizedId == null || normalizedId.isBlank()) throw new IllegalArgumentException("normalizedId must not be blank");
	}
}
