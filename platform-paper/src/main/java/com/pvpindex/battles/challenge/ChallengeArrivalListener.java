package com.pvpindex.battles.challenge;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for players arriving on this server after a cross-server challenge
 * transfer. When the expected player joins, a stabilisation delay (60 ticks /
 * 3 seconds) is applied before starting the battle, giving the server time to
 * fully load the player's data (inventory, position, etc.).
 */
public final class ChallengeArrivalListener implements Listener {

	private static final long STABILISATION_TICKS = 60L;
	private static final long TIMEOUT_MS = 10_000L;

	private final JavaPlugin plugin;
	private final ChallengeManager challengeManager;
	private final Map<UUID, PendingArrival> expected = new ConcurrentHashMap<>();

	public ChallengeArrivalListener(JavaPlugin plugin, ChallengeManager challengeManager) {
		this.plugin = plugin;
		this.challengeManager = challengeManager;

		Bukkit.getScheduler().runTaskTimer(plugin, this::expireStale, 100L, 100L);
	}

	/**
	 * Register that a target player is expected to arrive on this server for
	 * a challenge. When they join, the battle will start after stabilisation.
	 */
	public void expectArrival(UUID targetUuid, UUID challengerUuid, String modeId) {
		expected.put(targetUuid, new PendingArrival(challengerUuid, modeId, System.currentTimeMillis()));
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		PendingArrival arrival = expected.remove(event.getPlayer().getUniqueId());
		if (arrival == null) return;

		UUID targetUuid = event.getPlayer().getUniqueId();
		Bukkit.getScheduler().runTaskLater(plugin, () ->
				challengeManager.startBattle(arrival.challengerUuid(), targetUuid, arrival.modeId()),
				STABILISATION_TICKS);
	}

	private void expireStale() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, PendingArrival>> it = expected.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingArrival> entry = it.next();
			if (now - entry.getValue().createdMs() > TIMEOUT_MS) {
				it.remove();
			}
		}
	}

	private record PendingArrival(UUID challengerUuid, String modeId, long createdMs) {}
}
