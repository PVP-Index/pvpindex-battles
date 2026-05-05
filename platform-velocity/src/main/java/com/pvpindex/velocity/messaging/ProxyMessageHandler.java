package com.pvpindex.velocity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.pvpindex.velocity.registry.BattleRegistry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Proxy message handler bridging plugin messaging (Paper backends) and
 * Redis (lobby servers). Tracks pending legacy challenges so that
 * accepts/declines from lobby servers (arriving via Redis) can be
 * forwarded to the correct backend via plugin messaging.
 */
public final class ProxyMessageHandler {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannel.PROXY);

    private final PvPIndexVelocityPlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;

    private record PendingLegacyChallenge(
            String senderServer, UUID challengeId, UUID challengerUuid,
            String challengerName, UUID targetUuid, String targetServer,
            String modeId, Instant createdAt
    ) {}

    private final ConcurrentHashMap<UUID, PendingLegacyChallenge> pendingChallenges =
            new ConcurrentHashMap<>();

    public ProxyMessageHandler(PvPIndexVelocityPlugin plugin, ObjectMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();

        registerTransferHandler();
        registerRedisChallengeHandlers();
        scheduleExpiry();
    }

    private void registerTransferHandler() {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        router.addHandler(NetworkMessageType.TRANSFER_REQUEST, msg -> {
            String playerIdStr = msg.payloadString("playerId");
            String targetServer = msg.payloadString("targetServer");
            if (playerIdStr == null || targetServer == null) return;

            UUID playerId = UUID.fromString(playerIdStr);
            Optional<Player> player = plugin.getServer().getPlayer(playerId);
            Optional<RegisteredServer> server = plugin.getServer().getServer(targetServer);

            if (player.isPresent() && server.isPresent()) {
                plugin.markChallengeTransfer(playerId);
                player.get().createConnectionRequest(server.get()).fireAndForget();
                logger.info("[PvPIndex Transfer] Transferring " + player.get().getUsername()
                        + " to " + targetServer + " (requested by " + msg.payloadString("requestingNode") + ")");

                router.broadcast(NetworkMessageType.TRANSFER_READY, Map.of(
                        "playerId", playerIdStr,
                        "targetServer", targetServer,
                        "proxyId", plugin.config().networkConfig().proxyId()
                ));
            } else {
                router.broadcast(NetworkMessageType.TRANSFER_FAILED, Map.of(
                        "playerId", playerIdStr,
                        "targetServer", targetServer,
                        "reason", player.isEmpty() ? "player_not_found" : "server_not_found",
                        "proxyId", plugin.config().networkConfig().proxyId()
                ));
            }
        });
    }

    private void registerRedisChallengeHandlers() {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        router.addHandler(NetworkMessageType.CHALLENGE_ACCEPT, msg -> {
            UUID challengeId = msg.payloadUuid("challengeId");
            if (challengeId == null) return;

            PendingLegacyChallenge plc = pendingChallenges.remove(challengeId);
            if (plc == null) return;

            UUID accepterUuid = msg.payloadUuid("accepterUuid");
            UUID targetUuid = accepterUuid != null ? accepterUuid : plc.targetUuid();
            logger.info("[PvPIndex] Redis CHALLENGE_ACCEPT for challenge " + challengeId
                    + " - forwarding CONFIRMED to backend '" + plc.senderServer() + "'");

            plugin.backendMessenger().sendChallengeConfirmedExcluding(
                    plc.senderServer(), plc.challengeId(),
                    plc.challengerUuid(), targetUuid, plc.modeId(), targetUuid);

            if (plc.targetServer() != null && !plc.targetServer().equals(plc.senderServer())) {
                plugin.backendMessenger().sendChallengeCleanup(plc.targetServer(), challengeId);
            }

            transferTargetToBackend(targetUuid, plc.senderServer(), challengeId);
        });

        router.addHandler(NetworkMessageType.CHALLENGE_DENY, msg -> {
            UUID challengeId = msg.payloadUuid("challengeId");
            if (challengeId == null) return;

            PendingLegacyChallenge plc = pendingChallenges.remove(challengeId);
            if (plc == null) return;

            logger.info("[PvPIndex] Redis CHALLENGE_DENY for challenge " + challengeId
                    + " — forwarding REJECTED to backend '" + plc.senderServer() + "'");

            plugin.backendMessenger().sendChallengeRejected(
                    plc.senderServer(), plc.challengeId(), "declined");

            if (plc.targetServer() != null && !plc.targetServer().equals(plc.senderServer())) {
                plugin.backendMessenger().sendChallengeCleanup(plc.targetServer(), challengeId);
            }
        });
    }

    private void scheduleExpiry() {
        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    Instant cutoff = Instant.now().minusSeconds(120);
                    pendingChallenges.values().removeIf(plc -> plc.createdAt().isBefore(cutoff));
                })
                .repeat(java.time.Duration.ofSeconds(30))
                .schedule();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        if (!(event.getSource() instanceof ServerConnection backend)) return;

        event.setResult(ForwardResult.handled());

        String senderServer = backend.getServerInfo().getName();

        if (!plugin.config().isMonitored(senderServer)) {
            if (plugin.config().debug()) {
                logger.info("[ProxyMessageHandler] Ignoring message from unmonitored server: " + senderServer);
            }
            return;
        }

        BattleMessage msg;
        try {
            msg = BattleMessage.decode(mapper, event.getData());
        } catch (IOException e) {
            logger.warning("[ProxyMessageHandler] Failed to decode message from " + senderServer + ": " + e.getMessage());
            return;
        }

        if (!msg.isValid(plugin.config().paperSecret())) {
            logger.warning("[ProxyMessageHandler] Rejected message from '" + senderServer
                    + "' — invalid secret (type=" + msg.type() + "). "
                    + "Check that paper_secret matches proxy.secret in Paper's config.yml.");
            return;
        }

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] " + msg.type() + " from " + senderServer);
        }

        switch (msg.type()) {
            case BATTLE_START         -> handleBattleStart(senderServer, msg);
            case BATTLE_END           -> handleBattleEnd(msg);
            case PLAYER_ENTER_BATTLE  -> handlePlayerEnterBattle(msg);
            case PLAYER_LEAVE_BATTLE  -> handlePlayerLeaveBattle(msg);
            case HEARTBEAT            -> handleHeartbeat(senderServer, msg);
            // Challenge messages from legacy Paper servers (no lobby mode) are
            // still accepted and forwarded via plugin messaging for backward compat
            case CHALLENGE_SEND       -> handleLegacyChallengeSend(senderServer, msg);
            case CHALLENGE_ACCEPT     -> handleLegacyChallengeAccept(senderServer, msg);
            case CHALLENGE_DECLINE    -> handleLegacyChallengeDecline(senderServer, msg);
            default -> {
                if (plugin.config().debug()) {
                    logger.info("[ProxyMessageHandler] Unhandled message type from backend: " + msg.type());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBattleStart(String serverName, BattleMessage msg) {
        String battleUuidStr = (String) msg.data().get("battleUuid");
        List<Map<String, Object>> rawParticipants =
                (List<Map<String, Object>>) msg.data().get("participants");

        if (battleUuidStr == null || rawParticipants == null) {
            logger.warning("[ProxyMessageHandler] BATTLE_START missing required fields.");
            return;
        }

        UUID battleUuid;
        try { battleUuid = UUID.fromString(battleUuidStr); }
        catch (IllegalArgumentException e) { return; }

        Set<UUID> participants = new HashSet<>();
        for (Map<String, Object> p : rawParticipants) {
            try { participants.add(UUID.fromString((String) p.get("uuid"))); }
            catch (Exception ignored) {}
        }

        plugin.battleRegistry().register(battleUuid, serverName, participants, "IN_PROGRESS");
        logger.info("[PvPIndex] Battle started on '" + serverName + "': "
                + battleUuid + " (" + participants.size() + " participants)");

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            router.broadcast(NetworkMessageType.BATTLE_START, Map.of(
                    "battleUuid", battleUuidStr,
                    "server", serverName,
                    "proxyId", plugin.config().networkConfig().proxyId(),
                    "participantCount", participants.size()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBattleEnd(BattleMessage msg) {
        String battleUuidStr = (String) msg.data().get("battleUuid");
        if (battleUuidStr == null) return;

        UUID battleUuid;
        try { battleUuid = UUID.fromString(battleUuidStr); }
        catch (IllegalArgumentException e) { return; }

        BattleRegistry.BattleEntry entry = plugin.battleRegistry().get(battleUuid).orElse(null);
        if (entry != null) {
            logger.info("[PvPIndex] Battle ended on '" + entry.serverName() + "': "
                    + battleUuid + " (status=" + msg.data().get("status") + ")");
        }

        // Return transferred players using participant list from the message
        List<String> participantStrs = (List<String>) msg.data().get("participants");
        if (participantStrs != null) {
            Set<UUID> participants = new HashSet<>();
            for (String s : participantStrs) {
                try { participants.add(UUID.fromString(s)); }
                catch (IllegalArgumentException ignored) {}
            }
            returnTransferredPlayers(participants);
        } else if (entry != null) {
            returnTransferredPlayers(entry.participantUuids());
        }

        plugin.battleRegistry().remove(battleUuid);

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            router.broadcast(NetworkMessageType.BATTLE_END, Map.of(
                    "battleUuid", battleUuidStr,
                    "proxyId", plugin.config().networkConfig().proxyId()
            ));
        }
    }

    /**
     * After a battle ends, transfer any participants who were moved
     * cross-server for the challenge back to the server they came from.
     */
    private void returnTransferredPlayers(Set<UUID> participantUuids) {
        for (UUID playerUuid : participantUuids) {
            String originServer = plugin.removePlayerOriginServer(playerUuid);
            if (originServer == null) continue;

            Optional<Player> player = plugin.getServer().getPlayer(playerUuid);
            if (player.isEmpty() || !player.get().isActive()) continue;

            Optional<RegisteredServer> target = plugin.getServer().getServer(originServer);
            if (target.isEmpty()) {
                logger.warning("[PvPIndex Return] Origin server '" + originServer
                        + "' not found for " + playerUuid + " — cannot return.");
                continue;
            }

            String currentServer = player.get().getCurrentServer()
                    .map(c -> c.getServerInfo().getName()).orElse("unknown");
            if (currentServer.equals(originServer)) continue;

            plugin.markChallengeTransfer(playerUuid);
            plugin.getServer().getScheduler()
                    .buildTask(plugin, () -> {
                        if (player.get().isActive()) {
                            player.get().createConnectionRequest(target.get()).fireAndForget();
                            logger.info("[PvPIndex Return] Battle ended — returning "
                                    + player.get().getUsername() + " from " + currentServer
                                    + " to origin '" + originServer + "'");
                        }
                    })
                    .delay(2, TimeUnit.SECONDS)
                    .schedule();
        }
    }

    private void handlePlayerEnterBattle(BattleMessage msg) {
        String playerUuidStr = (String) msg.data().get("playerUuid");
        String battleUuidStr = (String) msg.data().get("battleUuid");
        if (playerUuidStr == null || battleUuidStr == null) return;

        try {
            plugin.battleRegistry().addParticipant(
                    UUID.fromString(battleUuidStr), UUID.fromString(playerUuidStr));
        } catch (IllegalArgumentException ignored) {}
    }

    private void handlePlayerLeaveBattle(BattleMessage msg) {
        String playerUuidStr = (String) msg.data().get("playerUuid");
        if (playerUuidStr == null) return;
        try { plugin.battleRegistry().removeParticipant(UUID.fromString(playerUuidStr)); }
        catch (IllegalArgumentException ignored) {}
    }

    private void handleHeartbeat(String serverName, BattleMessage msg) {
        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Heartbeat from '" + serverName
                    + "': activeBattles=" + msg.data().get("activeBattleCount"));
        }
    }

    private void transferTargetToBackend(UUID targetUuid, String backendServer, UUID challengeId) {
        Optional<Player> target = plugin.getServer().getPlayer(targetUuid);
        Optional<RegisteredServer> server = plugin.getServer().getServer(backendServer);

        if (target.isPresent() && server.isPresent()) {
            String currentServer = target.get().getCurrentServer()
                    .map(c -> c.getServerInfo().getName()).orElse("unknown");
            if (!currentServer.equals(backendServer)) {
                plugin.setPlayerOriginServer(targetUuid, currentServer);
                plugin.markChallengeTransfer(targetUuid);
                target.get().createConnectionRequest(server.get()).fireAndForget();
                logger.info("[PvPIndex Transfer] Challenge " + challengeId
                        + " - transferring " + target.get().getUsername()
                        + " from " + currentServer + " to " + backendServer);
            }
        } else {
            logger.warning("[PvPIndex Transfer] Cannot transfer target " + targetUuid
                    + " to " + backendServer + " - player="
                    + (target.isPresent() ? "found" : "not_found")
                    + " server=" + (server.isPresent() ? "found" : "not_found"));
        }
    }

    // Legacy challenge forwarding for Paper servers without lobby mode
    private void handleLegacyChallengeSend(String senderServer, BattleMessage msg) {
        String targetName = (String) msg.data().get("targetName");
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String challengerName = (String) msg.data().get("challengerName");
        String modeId = (String) msg.data().get("modeId");

        if (targetName == null || challengerUuidStr == null) return;

        UUID challengeId;
        try { challengeId = challengeIdStr != null ? UUID.fromString(challengeIdStr) : UUID.randomUUID(); }
        catch (IllegalArgumentException e) { challengeId = UUID.randomUUID(); }

        UUID challengerUuid;
        try { challengerUuid = UUID.fromString(challengerUuidStr); }
        catch (IllegalArgumentException e) { return; }

        String targetServer;
        UUID targetUuid;

        // First check if the target is on this proxy
        Optional<Player> target = plugin.getServer().getPlayer(targetName);
        if (target.isPresent() && target.get().getCurrentServer().isPresent()) {
            targetServer = target.get().getCurrentServer().get().getServerInfo().getName();
            targetUuid = target.get().getUniqueId();
        } else {
            // Fall back to the Redis player registry for cross-proxy lookup
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                var location = router.playerRegistry().findPlayerByName(targetName);
                if (location.isPresent()) {
                    targetUuid = location.get().playerId();
                    targetServer = location.get().serverName();
                    logger.info("[ProxyMessageHandler] Cross-proxy lookup: " + targetName
                            + " found on " + location.get().proxyId() + "/" + targetServer);
                } else {
                    plugin.backendMessenger().sendChallengeRejected(senderServer, challengeId, "player_not_found");
                    return;
                }
            } else {
                plugin.backendMessenger().sendChallengeRejected(senderServer, challengeId, "player_not_found");
                return;
            }
        }

        pendingChallenges.put(challengeId, new PendingLegacyChallenge(
                senderServer, challengeId, challengerUuid, challengerName,
                targetUuid, targetServer, modeId, Instant.now()));

        plugin.backendMessenger().sendChallengeForward(
                targetServer, challengeId, challengerName, challengerUuid, modeId, targetUuid);

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Tracked challenge " + challengeId
                    + " from " + senderServer + " -> " + targetServer);
        }
    }

    private void handleLegacyChallengeAccept(String senderServer, BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        String accepterUuidStr = (String) msg.data().get("accepterUuid");
        if (challengeIdStr == null) return;

        UUID challengeId;
        try { challengeId = UUID.fromString(challengeIdStr); }
        catch (IllegalArgumentException e) { return; }

        PendingLegacyChallenge plc = pendingChallenges.remove(challengeId);
        if (plc == null) {
            logger.warning("[ProxyMessageHandler] CHALLENGE_ACCEPT for unknown challenge " + challengeId);
            return;
        }

        UUID accepterUuid = null;
        if (accepterUuidStr != null) {
            try { accepterUuid = UUID.fromString(accepterUuidStr); }
            catch (IllegalArgumentException ignored) {}
        }

        logger.info("[ProxyMessageHandler] Forwarding CHALLENGE_CONFIRMED to '" + plc.senderServer()
                + "' for challenge " + challengeId);

        UUID targetUuid = accepterUuid != null ? accepterUuid : plc.targetUuid();

        plugin.backendMessenger().sendChallengeConfirmedExcluding(
                plc.senderServer(), plc.challengeId(), plc.challengerUuid(),
                targetUuid, plc.modeId(), targetUuid);

        if (!senderServer.equals(plc.senderServer())) {
            plugin.backendMessenger().sendChallengeCleanup(senderServer, challengeId);
        }

        transferTargetToBackend(targetUuid, plc.senderServer(), challengeId);
    }

    private void handleLegacyChallengeDecline(String senderServer, BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;

        UUID challengeId;
        try { challengeId = UUID.fromString(challengeIdStr); }
        catch (IllegalArgumentException e) { return; }

        PendingLegacyChallenge plc = pendingChallenges.remove(challengeId);
        if (plc == null) {
            logger.warning("[ProxyMessageHandler] CHALLENGE_DECLINE for unknown challenge " + challengeId);
            return;
        }

        logger.info("[ProxyMessageHandler] Forwarding CHALLENGE_REJECTED to '" + plc.senderServer()
                + "' for challenge " + challengeId);

        plugin.backendMessenger().sendChallengeRejected(
                plc.senderServer(), plc.challengeId(), "declined");

        if (!senderServer.equals(plc.senderServer())) {
            plugin.backendMessenger().sendChallengeCleanup(senderServer, challengeId);
        }
    }
}
