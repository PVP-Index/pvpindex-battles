package com.pvpindex.velocity.challenge;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a challenge request while it awaits the target player's response.
 * Stored in memory on the Velocity proxy and expires after the configured
 * timeout (default 30 s).
 */
public record PendingChallenge(
		UUID challengeId,
		UUID challengerUuid,
		String challengerServer,
		UUID targetUuid,
		String targetServer,
		String modeId,
		Instant createdAt
) {}
