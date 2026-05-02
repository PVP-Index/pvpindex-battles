package com.pvpindex.velocity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.network.NetworkMessage;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.PlayerRegistry;
import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.pvpindex.velocity.challenge.PendingChallenge;
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

public final class ProxyMessageHandler {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannel.PROXY);
    private static final long CHALLENGE_TIMEOUT_SECONDS = 30;

    private final PvPIndexVelocityPlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final Map<UUID, PendingChallenge> pendingChallenges = new ConcurrentHashMap<>();

    public ProxyMessageHandler(PvPIndexVelocityPlugin plugin, ObjectMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();

        plugin.getServer().getScheduler()
                .buildTask(plugin, this::expireStaleChallenge)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();

        registerCrossProxyHandlers();
    }

    private void registerCrossProxyHandlers() {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        router.addHandler(NetworkMessageType.CHALLENGE_SEND, this::handleCrossProxyChallengeReceive);
        router.addHandler(NetworkMessageType.CHALLENGE_ACCEPT, this::handleCrossProxyChallengeAcceptReceive);
        router.addHandler(NetworkMessageType.CHALLENGE_DENY, this::handleCrossProxyChallengeDeclineReceive);
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
            case CHALLENGE_SEND       -> handleChallengeSend(senderServer, msg);
            case CHALLENGE_ACCEPT     -> handleChallengeAccept(msg);
            case CHALLENGE_DECLINE    -> handleChallengeDecline(msg);
            default -> {
                if (plugin.config().debug()) {
                    logger.info("[ProxyMessageHandler] Unhandled message type from backend: " + msg.type());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Local handlers (Paper → Velocity)
    // -------------------------------------------------------------------------

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
        plugin.battleRegistry().remove(battleUuid);

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            router.broadcast(NetworkMessageType.BATTLE_END, Map.of(
                    "battleUuid", battleUuidStr,
                    "proxyId", plugin.config().networkConfig().proxyId()
            ));
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

    // -------------------------------------------------------------------------
    // Challenge handlers (Paper → Velocity → Paper)
    // -------------------------------------------------------------------------

    private void handleChallengeSend(String senderServer, BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String challengerName = (String) msg.data().get("challengerName");
        String targetName = (String) msg.data().get("targetName");
        String modeId = (String) msg.data().get("modeId");

        if (challengerUuidStr == null || challengerName == null || targetName == null) {
            logger.warning("[ProxyMessageHandler] CHALLENGE_SEND missing required fields.");
            return;
        }

        UUID challengerUuid;
        try { challengerUuid = UUID.fromString(challengerUuidStr); }
        catch (IllegalArgumentException e) { return; }

        UUID challengeId;
        if (challengeIdStr != null) {
            try { challengeId = UUID.fromString(challengeIdStr); }
            catch (IllegalArgumentException e) { challengeId = UUID.randomUUID(); }
        } else {
            challengeId = UUID.randomUUID();
        }

        Optional<Player> targetPlayer = plugin.getServer().getPlayer(targetName);
        if (targetPlayer.isPresent()) {
            handleLocalChallenge(senderServer, challengeId, challengerUuid, challengerName,
                    targetPlayer.get(), modeId);
            return;
        }

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            Optional<PlayerRegistry.PlayerLocation> remoteLoc =
                    router.playerRegistry().findPlayerByName(targetName);
            if (remoteLoc.isPresent()) {
                handleCrossProxyChallenge(senderServer, challengeId, challengerUuid,
                        challengerName, remoteLoc.get(), modeId);
                return;
            }
        }

        plugin.backendMessenger().sendChallengeRejected(
                senderServer, challengeId, "player_not_found");
    }

    private void handleLocalChallenge(String senderServer, UUID challengeId,
                                      UUID challengerUuid, String challengerName,
                                      Player target, String modeId) {
        Optional<ServerConnection> targetConn = target.getCurrentServer();
        if (targetConn.isEmpty()) {
            plugin.backendMessenger().sendChallengeRejected(
                    senderServer, challengeId, "player_not_connected");
            return;
        }

        String targetServer = targetConn.get().getServerInfo().getName();

        PendingChallenge pending = new PendingChallenge(
                challengeId, challengerUuid, senderServer,
                target.getUniqueId(), targetServer, modeId, Instant.now());
        pendingChallenges.put(challengeId, pending);

        plugin.backendMessenger().sendChallengeForward(
                targetServer, challengeId, challengerName, challengerUuid,
                modeId, target.getUniqueId());

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Challenge " + challengeId
                    + " from " + challengerName + "@" + senderServer
                    + " -> " + target.getUsername() + "@" + targetServer
                    + " mode=" + modeId);
        }
    }

    private void handleCrossProxyChallenge(String senderServer, UUID challengeId,
                                           UUID challengerUuid, String challengerName,
                                           PlayerRegistry.PlayerLocation targetLoc, String modeId) {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        PendingChallenge pending = new PendingChallenge(
                challengeId, challengerUuid, senderServer,
                targetLoc.playerId(), "__cross_proxy__:" + targetLoc.proxyId(),
                modeId, Instant.now());
        pendingChallenges.put(challengeId, pending);

        router.sendToProxy(targetLoc.proxyId(), NetworkMessageType.CHALLENGE_SEND, Map.of(
                "challengeId", challengeId.toString(),
                "challengerUuid", challengerUuid.toString(),
                "challengerName", challengerName,
                "targetUuid", targetLoc.playerId().toString(),
                "targetName", targetLoc.playerName(),
                "modeId", modeId != null ? modeId : "",
                "originProxy", plugin.config().networkConfig().proxyId(),
                "originServer", senderServer
        ));

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Cross-proxy challenge " + challengeId
                    + " from " + challengerName + "@" + senderServer
                    + " -> " + targetLoc.playerName() + "@" + targetLoc.proxyId()
                    + " mode=" + modeId);
        }
    }

    private void handleChallengeAccept(BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;

        UUID challengeId;
        try { challengeId = UUID.fromString(challengeIdStr); }
        catch (IllegalArgumentException e) { return; }

        PendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) {
            if (plugin.config().debug()) {
                logger.info("[ProxyMessageHandler] CHALLENGE_ACCEPT for unknown/expired challenge " + challengeId);
            }
            return;
        }

        if (pending.targetServer().startsWith("__cross_proxy__:")) {
            String targetProxy = pending.targetServer().substring("__cross_proxy__:".length());
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(targetProxy, NetworkMessageType.CHALLENGE_ACCEPT, Map.of(
                        "challengeId", challengeId.toString(),
                        "originProxy", plugin.config().networkConfig().proxyId(),
                        "originServer", pending.challengerServer(),
                        "challengerUuid", pending.challengerUuid().toString(),
                        "modeId", pending.modeId() != null ? pending.modeId() : ""
                ));
            }
            return;
        }

        boolean sameServer = pending.challengerServer().equals(pending.targetServer());

        if (!sameServer) {
            plugin.markChallengeTransfer(pending.targetUuid());

            plugin.backendMessenger().sendChallengeConfirmedExcluding(
                    pending.challengerServer(), challengeId,
                    pending.challengerUuid(), pending.targetUuid(), pending.modeId(),
                    pending.targetUuid());

            plugin.backendMessenger().sendChallengeCleanup(
                    pending.targetServer(), challengeId);

            Optional<Player> targetPlayer = plugin.getServer().getPlayer(pending.targetUuid());
            Optional<RegisteredServer> challengerServer = plugin.getServer().getServer(pending.challengerServer());
            if (targetPlayer.isPresent() && challengerServer.isPresent()) {
                targetPlayer.get().createConnectionRequest(challengerServer.get()).fireAndForget();
                if (plugin.config().debug()) {
                    logger.info("[ProxyMessageHandler] Transferring target "
                            + pending.targetUuid() + " from " + pending.targetServer()
                            + " to " + pending.challengerServer());
                }
            }
        } else {
            plugin.backendMessenger().sendChallengeConfirmed(
                    pending.challengerServer(), challengeId,
                    pending.challengerUuid(), pending.targetUuid(), pending.modeId());
        }

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Challenge " + challengeId
                    + " ACCEPTED, notifying " + pending.challengerServer()
                    + (sameServer ? " (same server)" : " + CLEANUP to " + pending.targetServer() + " (cross-server)"));
        }
    }

    private void handleChallengeDecline(BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;

        UUID challengeId;
        try { challengeId = UUID.fromString(challengeIdStr); }
        catch (IllegalArgumentException e) { return; }

        PendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) return;

        if (pending.targetServer().startsWith("__cross_proxy__:")) {
            String targetProxy = pending.targetServer().substring("__cross_proxy__:".length());
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(targetProxy, NetworkMessageType.CHALLENGE_DENY, Map.of(
                        "challengeId", challengeId.toString()
                ));
            }
        }

        plugin.backendMessenger().sendChallengeRejected(
                pending.challengerServer(), challengeId, "declined");

        if (plugin.config().debug()) {
            logger.info("[ProxyMessageHandler] Challenge " + challengeId + " DECLINED");
        }
    }

    // -------------------------------------------------------------------------
    // Cross-proxy incoming handlers (Redis → this proxy)
    // -------------------------------------------------------------------------

    private void handleCrossProxyChallengeReceive(NetworkMessage msg) {
        String targetUuidStr = msg.payloadString("targetUuid");
        String challengeIdStr = msg.payloadString("challengeId");
        String challengerName = msg.payloadString("challengerName");
        String challengerUuidStr = msg.payloadString("challengerUuid");
        String modeId = msg.payloadString("modeId");
        String originProxy = msg.payloadString("originProxy");
        String originServer = msg.payloadString("originServer");

        if (targetUuidStr == null || challengeIdStr == null) return;

        UUID targetUuid = UUID.fromString(targetUuidStr);
        UUID challengeId = UUID.fromString(challengeIdStr);

        Optional<Player> target = plugin.getServer().getPlayer(targetUuid);
        if (target.isEmpty()) {
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(originProxy, NetworkMessageType.CHALLENGE_DENY, Map.of(
                        "challengeId", challengeIdStr,
                        "reason", "player_not_found"
                ));
            }
            return;
        }

        target.get().getCurrentServer().ifPresent(conn -> {
            String targetServer = conn.getServerInfo().getName();
            plugin.backendMessenger().sendChallengeForward(
                    targetServer, challengeId, challengerName,
                    UUID.fromString(challengerUuidStr), modeId, targetUuid);
        });
    }

    private void handleCrossProxyChallengeAcceptReceive(NetworkMessage msg) {
        String challengeIdStr = msg.payloadString("challengeId");
        String originServer = msg.payloadString("originServer");
        String challengerUuidStr = msg.payloadString("challengerUuid");
        String modeId = msg.payloadString("modeId");

        if (challengeIdStr == null || originServer == null || challengerUuidStr == null) return;

        UUID challengeId = UUID.fromString(challengeIdStr);
        UUID challengerUuid = UUID.fromString(challengerUuidStr);

        PendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending != null) {
            plugin.backendMessenger().sendChallengeConfirmed(
                    pending.challengerServer(), challengeId,
                    pending.challengerUuid(), pending.targetUuid(),
                    pending.modeId());
        }
    }

    private void handleCrossProxyChallengeDeclineReceive(NetworkMessage msg) {
        String challengeIdStr = msg.payloadString("challengeId");
        if (challengeIdStr == null) return;

        UUID challengeId = UUID.fromString(challengeIdStr);
        PendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending != null) {
            String reason = msg.payloadString("reason");
            plugin.backendMessenger().sendChallengeRejected(
                    pending.challengerServer(), challengeId,
                    reason != null ? reason : "declined");
        }
    }

    private void expireStaleChallenge() {
        Instant cutoff = Instant.now().minusSeconds(CHALLENGE_TIMEOUT_SECONDS);
        Iterator<Map.Entry<UUID, PendingChallenge>> it = pendingChallenges.entrySet().iterator();
        while (it.hasNext()) {
            PendingChallenge p = it.next().getValue();
            if (p.createdAt().isBefore(cutoff)) {
                it.remove();
                plugin.backendMessenger().sendChallengeRejected(
                        p.challengerServer(), p.challengeId(), "timeout");
                if (!p.challengerServer().equals(p.targetServer())) {
                    plugin.backendMessenger().sendChallengeRejected(
                            p.targetServer(), p.challengeId(), "timeout");
                }
                if (plugin.config().debug()) {
                    logger.info("[ProxyMessageHandler] Challenge " + p.challengeId() + " EXPIRED");
                }
            }
        }
    }
}
