package com.pvpindex.battles.challenge;

import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gui.GuiConfig;
import com.pvpindex.battles.messaging.NetworkPlayerCache;
import com.pvpindex.battles.messaging.PaperMessenger;
import com.pvpindex.battles.network.ChallengeSyncService;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.teams.TeamsGuardService;
import com.pvpindex.battles.util.DebugLogger;
import com.pvpindex.battles.util.MessageService;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages the full lifecycle of player-to-player challenges.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Proxy mode</b> — routes all challenges through Velocity via
 *       {@link PaperMessenger}. Supports cross-server challenges with
 *       automatic player transfer.</li>
 *   <li><b>Standalone mode</b> — resolves targets locally with
 *       {@link Bukkit#getPlayerExact(String)} and handles everything
 *       on the same server.</li>
 * </ul>
 */
public final class ChallengeManager {

	private final JavaPlugin plugin;
	private final PaperMessenger paperMessenger;
	private final BattleQueueService queueService;
	private final GameModeRegistry gameModeRegistry;
	private final DebugLogger debug;
	private final GuiConfig guiConfig;
	private final boolean proxyEnabled;
	private final MessageService messageService;
	private ChallengeArrivalListener arrivalListener;

	private final Map<UUID, LocalChallenge> pending = new ConcurrentHashMap<>();
	private ChallengeSyncService challengeSyncService;
	private NetworkPlayerCache lobbyPlayerCache;
	private com.pvpindex.battles.network.TransferRequester transferRequester;
	private String velocityServerName;
	private TeamsGuardService teamsGuard;

	public ChallengeManager(JavaPlugin plugin, PaperMessenger paperMessenger,
			BattleQueueService queueService, GameModeRegistry gameModeRegistry,
			DebugLogger debug, GuiConfig guiConfig, boolean proxyEnabled,
			MessageService messageService) {
		this.plugin = plugin;
		this.paperMessenger = paperMessenger;
		this.queueService = queueService;
		this.gameModeRegistry = gameModeRegistry;
		this.debug = debug;
		this.guiConfig = guiConfig;
		this.proxyEnabled = proxyEnabled;
		this.messageService = messageService;

		plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireStale, 100L, 100L);
	}

	public void setArrivalListener(ChallengeArrivalListener arrivalListener) {
		this.arrivalListener = arrivalListener;
	}

	public void setTeamsGuard(TeamsGuardService teamsGuard) {
		this.teamsGuard = teamsGuard;
	}

	/**
	 * Enables lobby-direct challenge routing via Redis.
	 * When set, challenges bypass the proxy and go lobby-to-lobby.
	 */
	public void setLobbyServices(ChallengeSyncService syncService, NetworkPlayerCache playerCache,
			com.pvpindex.battles.network.TransferRequester transferRequester, String velocityServerName) {
		this.challengeSyncService = syncService;
		this.lobbyPlayerCache = playerCache;
		this.transferRequester = transferRequester;
		this.velocityServerName = velocityServerName;

		syncService.setChallengeReceivedCallback(this::handleIncomingChallenge);
		syncService.setChallengeAcceptedCallback(this::handleChallengeConfirmed);
		syncService.setChallengeDeniedCallback((challengeId, challengerUuid) -> {
			handleChallengeRejected(challengeId, "declined");
		});
	}

	public boolean isLobbyMode() {
		return challengeSyncService != null;
	}

	// ── Outbound: send a challenge ──────────────────────────────────────────

	public void sendChallenge(Player challenger, String targetName, String modeId) {
		if (challenger.getName().equalsIgnoreCase(targetName)) {
			messageService.send(challenger, "challenge.cannot_self");
			return;
		}

		if (hasOutgoing(challenger.getUniqueId(), targetName)) {
			messageService.send(challenger, "challenge.already_pending");
			return;
		}

		// TeamsAPI guard — block same-team challenges when enabled (standalone only;
		// cross-server challenges are re-checked in startBattle when both UUIDs are known).
		if (teamsGuard != null && teamsGuard.isEnabled()) {
			Player localTarget = Bukkit.getPlayerExact(targetName);
			if (localTarget != null && localTarget.isOnline()) {
				if (teamsGuard.isSameTeam(challenger.getUniqueId(), localTarget.getUniqueId())) {
					messageService.send(challenger, "challenge.same_team");
					return;
				}
			}
		}

		UUID challengeId = UUID.randomUUID();

		if (challengeSyncService != null && lobbyPlayerCache != null) {
			// Lobby mode: always check local player first to avoid Redis self-routing
			Player localTarget = Bukkit.getPlayerExact(targetName);
			if (localTarget != null && localTarget.isOnline()) {
				LocalChallenge lc = new LocalChallenge(challengeId, challenger.getUniqueId(),
						challenger.getName(), localTarget.getUniqueId(), targetName, modeId, Instant.now(), false);
				pending.put(challengeId, lc);
				showChallengeToTarget(localTarget, challengeId, challenger.getName(), modeId);
				messageService.send(challenger, "challenge.sent", "%target%", targetName);
				debug.logChallenge("SEND_LOCAL", challengeId, challenger.getName() + " -> " + targetName);
				return;
			}

			// Not local - look up remote player in the network cache
			NetworkPlayerCache.NetworkPlayer target = lobbyPlayerCache.findByName(targetName);
			if (target == null) {
				messageService.send(challenger, "general.player_not_found");
				return;
			}

			LocalChallenge lc = new LocalChallenge(challengeId, challenger.getUniqueId(),
					challenger.getName(), target.uuid(), targetName, modeId, Instant.now(), false);
			pending.put(challengeId, lc);

			String targetNodeId = target.server();
			challengeSyncService.sendChallenge(challengeId, challenger.getUniqueId(),
					challenger.getName(), targetName, target.uuid(), targetNodeId, modeId);
			messageService.send(challenger, "challenge.sent", "%target%", targetName);
			debug.logChallenge("SEND_REDIS", challengeId, challenger.getName() + " -> " + targetName + " mode=" + modeId);
		} else if (proxyEnabled && paperMessenger != null) {
			LocalChallenge lc = new LocalChallenge(challengeId, challenger.getUniqueId(),
					challenger.getName(), null, targetName, modeId, Instant.now(), false);
			pending.put(challengeId, lc);

			paperMessenger.sendChallengeSend(challengeId, challenger.getUniqueId(),
					challenger.getName(), targetName, modeId);
			messageService.send(challenger, "challenge.sent", "%target%", targetName);
			debug.logChallenge("SEND_PROXY", challengeId, challenger.getName() + " -> " + targetName + " mode=" + modeId);
		} else {
			Player target = Bukkit.getPlayerExact(targetName);
			if (target == null || !target.isOnline()) {
				messageService.send(challenger, "general.player_not_found");
				return;
			}
			LocalChallenge lc = new LocalChallenge(challengeId, challenger.getUniqueId(),
					challenger.getName(), target.getUniqueId(), targetName, modeId, Instant.now(), false);
			pending.put(challengeId, lc);
			showChallengeToTarget(target, challengeId, challenger.getName(), modeId);
			messageService.send(challenger, "challenge.sent", "%target%", targetName);
			debug.logChallenge("SEND_LOCAL", challengeId, challenger.getName() + " -> " + targetName);
		}
	}

	// ── Inbound from Velocity (CHALLENGE_FORWARD) ───────────────────────────

	public void handleIncomingChallenge(UUID challengeId, String challengerName,
			UUID challengerUuid, String modeId, UUID targetUuid) {
		Player target = Bukkit.getPlayer(targetUuid);
		if (target == null || !target.isOnline()) return;

		LocalChallenge lc = new LocalChallenge(challengeId, challengerUuid,
				challengerName, targetUuid, target.getName(), modeId, Instant.now(), false);
		pending.put(challengeId, lc);
		showChallengeToTarget(target, challengeId, challengerName, modeId);
		debug.logChallenge("FORWARD_RECEIVED", challengeId, challengerName + " -> " + target.getName());
	}

	// ── Accept / Decline ────────────────────────────────────────────────────

	public void acceptChallenge(Player player, UUID challengeId) {
		LocalChallenge lc = pending.get(challengeId);
		if (lc == null || lc.accepted()) {
			messageService.send(player, "challenge.invalid_or_expired");
			return;
		}

		if (challengeSyncService != null) {
			boolean isRedisChallenge = challengeSyncService.pendingChallenges().containsKey(challengeId);
			if (isRedisChallenge) {
				pending.put(challengeId, lc.withAccepted(true));
				challengeSyncService.acceptChallenge(challengeId, player.getUniqueId());
				messageService.send(player, "challenge.accepted");
				debug.logChallenge("ACCEPT_REDIS", challengeId, player.getName());
			} else if (proxyEnabled && paperMessenger != null) {
				pending.put(challengeId, lc.withAccepted(true));
				paperMessenger.sendChallengeAccept(challengeId, player.getUniqueId());
				messageService.send(player, "challenge.accepted");
				debug.logChallenge("ACCEPT_PROXY", challengeId, player.getName());
			} else {
				pending.put(challengeId, lc.withAccepted(true));
				challengeSyncService.acceptChallenge(challengeId, player.getUniqueId());
				messageService.send(player, "challenge.accepted");
				debug.logChallenge("ACCEPT_REDIS_FALLBACK", challengeId, player.getName());
			}
		} else if (proxyEnabled && paperMessenger != null) {
			pending.put(challengeId, lc.withAccepted(true));
			paperMessenger.sendChallengeAccept(challengeId, player.getUniqueId());
			messageService.send(player, "challenge.accepted");
			debug.logChallenge("ACCEPT_PROXY", challengeId, player.getName());
		} else {
			pending.remove(challengeId);
			startLocalBattle(lc);
			messageService.send(player, "challenge.accepted");
			debug.logChallenge("ACCEPT_LOCAL", challengeId, player.getName());
		}
	}

	public void declineChallenge(Player player, UUID challengeId) {
		LocalChallenge lc = pending.remove(challengeId);
		if (lc == null) {
			messageService.send(player, "challenge.invalid_or_expired");
			return;
		}

		if (challengeSyncService != null) {
			boolean isRedisChallenge = challengeSyncService.pendingChallenges().containsKey(challengeId);
			if (isRedisChallenge) {
				challengeSyncService.declineChallenge(challengeId, player.getUniqueId());
			} else if (proxyEnabled && paperMessenger != null) {
				paperMessenger.sendChallengeDecline(challengeId, player.getUniqueId());
			} else {
				challengeSyncService.declineChallenge(challengeId, player.getUniqueId());
			}
		} else if (proxyEnabled && paperMessenger != null) {
			paperMessenger.sendChallengeDecline(challengeId, player.getUniqueId());
		} else {
			Player challenger = Bukkit.getPlayer(lc.challengerUuid());
			if (challenger != null && challenger.isOnline()) {
				messageService.send(challenger, "challenge.declined_notify", "%player%", player.getName());
			}
		}
		messageService.send(player, "challenge.declined");
		debug.logChallenge("DECLINE", challengeId, player.getName());
	}

	// ── Velocity callbacks (CHALLENGE_CONFIRMED / CHALLENGE_REJECTED) ───────

	/**
	 * Called when a challenge is accepted (from proxy or Redis).
	 * Only the battle-hosting server (challenger's server) receives this.
	 * May be called from the Redis subscriber thread, so all Bukkit work
	 * is posted to the main thread via the scheduler.
	 */
	public void handleChallengeConfirmed(UUID challengeId, UUID challengerUuid,
			UUID targetUuid, String modeId) {
		LocalChallenge lc = pending.remove(challengeId);

		UUID cUuid = lc != null ? lc.challengerUuid() : challengerUuid;
		UUID tUuid = lc != null && lc.targetUuid() != null ? lc.targetUuid() : targetUuid;
		String mode = lc != null && lc.modeId() != null ? lc.modeId() : modeId;
		String targetName = lc != null ? lc.targetName() : "";

		debug.logChallenge("CONFIRMED", challengeId,
				"challenger=" + cUuid + " target=" + tUuid + " mode=" + mode);

		Bukkit.getScheduler().runTask(plugin, () -> {
			Player challenger = Bukkit.getPlayer(cUuid);
			if (challenger != null && challenger.isOnline()) {
				messageService.send(challenger, "challenge.accepted_notify",
						"%player%", targetName);
			}

			Player target = Bukkit.getPlayer(tUuid);
			if (target != null && target.isOnline()) {
				Bukkit.getScheduler().runTaskLater(plugin,
						() -> startBattle(cUuid, tUuid, mode), 20L);
			} else if (arrivalListener != null) {
				arrivalListener.expectArrival(tUuid, cUuid, mode);
				if (transferRequester != null && velocityServerName != null && !velocityServerName.isEmpty()) {
					transferRequester.requestTransfer(tUuid, velocityServerName, "challenge_accept");
					debug.logChallenge("TRANSFER_REQUESTED", challengeId,
							"target=" + tUuid + " -> " + velocityServerName);
				}
			} else {
				debug.logChallenge("CONFIRMED_NO_ARRIVAL_LISTENER", challengeId,
						"Target not online and no arrival listener - cannot start battle");
			}
		});
	}

	/**
	 * Called when Velocity sends CHALLENGE_CLEANUP to the non-hosting server.
	 * Just removes the pending challenge; no battle attempt.
	 */
	public void handleChallengeCleanup(UUID challengeId) {
		LocalChallenge lc = pending.remove(challengeId);
		debug.logChallenge("CLEANUP", challengeId,
				lc != null ? "removed pending" : "no local record");
	}

	/**
	 * Starts a direct battle between two players (by UUID). Called after
	 * stabilisation delay or by the arrival listener.
	 */
	public void startBattle(UUID challengerUuid, UUID targetUuid, String modeId) {
		Player challenger = Bukkit.getPlayer(challengerUuid);
		Player target = Bukkit.getPlayer(targetUuid);

		if (challenger == null || !challenger.isOnline()) {
			if (target != null && target.isOnline()) {
				messageService.send(target, "challenge.opponent_offline");
			}
			debug.logChallenge("START_FAILED", null, "Challenger offline");
			return;
		}
		if (target == null || !target.isOnline()) {
			messageService.send(challenger, "challenge.opponent_offline");
			debug.logChallenge("START_FAILED", null, "Target offline");
			return;
		}

		if (modeId == null || modeId.isBlank()) {
			messageService.send(challenger, "challenge.no_mode");
			messageService.send(target, "challenge.no_mode");
			return;
		}

		Optional<GameModeDefinition> modeOpt = gameModeRegistry.findMode(modeId);
		if (modeOpt.isEmpty()) {
			messageService.send(challenger, "challenge.unknown_mode", "%mode%", modeId);
			messageService.send(target, "challenge.unknown_mode", "%mode%", modeId);
			return;
		}

		// TeamsAPI guard — final check with both UUIDs known (covers all modes).
		if (teamsGuard != null && teamsGuard.isSameTeam(challengerUuid, targetUuid)) {
			messageService.send(challenger, "challenge.same_team");
			messageService.send(target, "challenge.same_team_target", "%player%", challenger.getName());
			debug.logChallenge("SAME_TEAM_BLOCKED", null,
					challenger.getName() + " vs " + target.getName());
			return;
		}

		boolean started = queueService.startDirect(challenger, target, modeOpt.get());
		if (started) {
			debug.logChallenge("BATTLE_STARTED", null,
					challenger.getName() + " vs " + target.getName() + " mode=" + modeId);
		}
	}

	public void handleChallengeRejected(UUID challengeId, String reason) {
		LocalChallenge lc = pending.remove(challengeId);
		debug.logChallenge("REJECTED", challengeId, reason);

		if (lc == null) return;

		Bukkit.getScheduler().runTask(plugin, () -> {
			Player challenger = Bukkit.getPlayer(lc.challengerUuid());
			if (challenger != null && challenger.isOnline()) {
				switch (reason) {
					case "timeout" -> messageService.send(challenger, "challenge.timed_out");
					case "player_not_found" -> messageService.send(challenger, "general.player_not_found");
					case "player_not_connected" -> messageService.send(challenger, "challenge.opponent_offline");
					case "declined" -> messageService.send(challenger, "challenge.declined_notify",
							"%player%", lc.targetName());
					default -> messageService.send(challenger, "challenge.rejected", "%reason%", reason);
				}
			}
		});

		if ("timeout".equals(reason) && lc.targetUuid() != null) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				Player target = Bukkit.getPlayer(lc.targetUuid());
				if (target != null && target.isOnline()) {
					messageService.send(target, "challenge.expired");
				}
			});
		}
	}

	// ── UI (chat-only) ──────────────────────────────────────────────────────

	private void showChallengeToTarget(Player target, UUID challengeId, String challengerName, String modeId) {
		String modeName = modeId != null && !modeId.isBlank()
				? gameModeRegistry.findMode(modeId).map(GameModeDefinition::displayName).orElse(modeId)
				: modeId;

		Optional<GameModeDefinition> modeOpt = modeId != null
				? gameModeRegistry.findMode(modeId) : Optional.empty();
		boolean isSmpRisk = modeOpt.isPresent()
				&& modeOpt.get().rules() != null
				&& modeOpt.get().rules().usePlayerInventory();

		messageService.sendRaw(target, "challenge.header",
				"%challenger%", challengerName, "%mode%", modeName != null ? modeName : "");
		if (isSmpRisk) {
			messageService.send(target, "challenge.smp_warning");
		}
		TextComponent acceptBtn = new TextComponent(messageService.component("challenge.accept_label"));
		acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				"/battle accept " + challengeId));
		acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(messageService.component("challenge.accept_hover")).create()));
		TextComponent declineBtn = new TextComponent(messageService.component("challenge.decline_label"));
		declineBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				"/battle decline " + challengeId));
		declineBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(messageService.component("challenge.decline_hover")).create()));
		target.spigot().sendMessage(new TextComponent("  "), acceptBtn, new TextComponent(" "), declineBtn);
	}

	// ── Internals ───────────────────────────────────────────────────────────

	private void startLocalBattle(LocalChallenge lc) {
		if (lc.targetUuid() == null) return;
		startBattle(lc.challengerUuid(), lc.targetUuid(), lc.modeId());
	}

	private boolean hasOutgoing(UUID challengerUuid, String targetName) {
		return pending.values().stream()
				.anyMatch(lc -> lc.challengerUuid().equals(challengerUuid)
						&& targetName.equalsIgnoreCase(lc.targetName()));
	}

	private void expireStale() {
		long timeoutSeconds = guiConfig.challengeTimeoutSeconds();
		Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
		Iterator<Map.Entry<UUID, LocalChallenge>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			LocalChallenge lc = it.next().getValue();
			if (lc.createdAt().isBefore(cutoff)) {
				it.remove();
				Player challenger = Bukkit.getPlayer(lc.challengerUuid());
				if (challenger != null && challenger.isOnline()) {
					messageService.send(challenger, "challenge.timed_out");
				}
				if (lc.targetUuid() != null) {
					Player target = Bukkit.getPlayer(lc.targetUuid());
					if (target != null && target.isOnline()) {
						messageService.send(target, "challenge.expired");
					}
				}
			}
		}
	}

	public Collection<UUID> pendingChallengeIds() {
		return pending.keySet();
	}

	/**
	 * Returns challenge IDs where the given player is the target (for accept/decline
	 * tab completion). Only shows challenges relevant to the requesting player.
	 */
	public Collection<UUID> pendingChallengeIdsFor(UUID targetUuid) {
		return pending.entrySet().stream()
				.filter(e -> targetUuid.equals(e.getValue().targetUuid()))
				.map(Map.Entry::getKey)
				.toList();
	}

	/**
	 * Challenge record held locally while awaiting a response.
	 * {@code targetUuid} may be null in proxy mode until Velocity resolves the player.
	 * {@code targetName} is always set for duplicate-send detection.
	 */
	public record LocalChallenge(
			UUID challengeId,
			UUID challengerUuid,
			String challengerName,
			UUID targetUuid,
			String targetName,
			String modeId,
			Instant createdAt,
			boolean accepted
	) {
		public LocalChallenge withAccepted(boolean accepted) {
			return new LocalChallenge(challengeId, challengerUuid, challengerName,
					targetUuid, targetName, modeId, createdAt, accepted);
		}
	}
}
