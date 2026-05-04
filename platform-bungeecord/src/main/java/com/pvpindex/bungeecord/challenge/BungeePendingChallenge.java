package com.pvpindex.bungeecord.challenge;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a challenge request while it awaits the target player's response.
 * Stored in memory on the BungeeCord proxy and expires after the configured
 * timeout (default 30 s).
 *
 * <p>For cross-proxy challenges, two entries exist across the network:
 * <ul>
 *   <li>On the <b>origin</b> proxy: {@code targetServer} is {@code "__cross_proxy__:<proxyId>"},
 *       {@code originProxy} is {@code null} (we are the origin).</li>
 *   <li>On the <b>receiving</b> proxy: {@code originProxy} is set to the sender's proxy ID
 *       so accepts/declines can be routed back via Redis.</li>
 * </ul>
 */
public record BungeePendingChallenge(
		UUID challengeId,
		UUID challengerUuid,
		String challengerServer,
		UUID targetUuid,
		String targetServer,
		String modeId,
		Instant createdAt,
		String originProxy
) {
	public BungeePendingChallenge(UUID challengeId, UUID challengerUuid,
								  String challengerServer, UUID targetUuid,
								  String targetServer, String modeId,
								  Instant createdAt) {
		this(challengeId, challengerUuid, challengerServer, targetUuid,
				targetServer, modeId, createdAt, null);
	}

	public boolean isCrossProxyOrigin() {
		return targetServer != null && targetServer.startsWith("__cross_proxy__:");
	}

	public boolean isCrossProxyReceiver() {
		return originProxy != null;
	}

	public String crossProxyTargetId() {
		return isCrossProxyOrigin()
				? targetServer.substring("__cross_proxy__:".length())
				: null;
	}
}
