package com.pvpindex.battles.messaging;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the list of online players across the entire Velocity network.
 * Updated periodically by {@code NETWORK_PLAYER_LIST} messages from the
 * proxy. Falls back to an empty set when proxy messaging is inactive.
 *
 * <p>Thread-safe: reads and writes may happen from any thread.</p>
 */
public final class NetworkPlayerCache {

	private volatile List<NetworkPlayer> players = List.of();
	private volatile long lastUpdateEpochMs = 0;

	/**
	 * Replace the entire cached player list. Called when a
	 * {@code NETWORK_PLAYER_LIST} message arrives from Velocity.
	 */
	public void update(List<NetworkPlayer> newPlayers) {
		this.players = List.copyOf(newPlayers);
		this.lastUpdateEpochMs = System.currentTimeMillis();
	}

	/** All player names currently known across the network. */
	public List<String> allNames() {
		return players.stream().map(NetworkPlayer::name).toList();
	}

	/** Full player records. */
	public List<NetworkPlayer> allPlayers() {
		return players;
	}

	/** Millisecond epoch of the last successful update, or 0 if never updated. */
	public long lastUpdateEpochMs() {
		return lastUpdateEpochMs;
	}

	/** Whether the cache has received at least one update from the proxy. */
	public boolean isPopulated() {
		return lastUpdateEpochMs > 0;
	}

	public record NetworkPlayer(String name, UUID uuid, String server) {}
}
