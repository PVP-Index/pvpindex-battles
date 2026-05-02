package com.pvpindex.battles.util;

import com.pvpindex.battles.common.messaging.MessageType;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Conditional logger gated behind the {@code debug: true/false} config flag.
 * All methods are no-ops when disabled, keeping hot paths free of logging
 * overhead.
 */
public final class DebugLogger {

	private final Logger logger;
	private final boolean enabled;

	public DebugLogger(Logger logger, boolean enabled) {
		this.logger = logger;
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void log(String message) {
		if (enabled) logger.info("[Debug] " + message);
	}

	public void logVelocity(String direction, MessageType type, Map<String, Object> data) {
		if (!enabled) return;
		logger.info("[Debug][Velocity] " + direction + " " + type
				+ (data != null ? " data=" + data : ""));
	}

	public void logMapping(String rawId, String normalizedId) {
		if (!enabled) return;
		logger.info("[Debug][Mapping] " + rawId + " -> " + normalizedId);
	}

	public void logGui(String action, String detail) {
		if (!enabled) return;
		logger.info("[Debug][GUI] " + action + ": " + detail);
	}

	public void logChallenge(String event, UUID challengeId, String detail) {
		if (!enabled) return;
		logger.info("[Debug][Challenge] " + event + " id=" + challengeId
				+ (detail != null ? " " + detail : ""));
	}
}
