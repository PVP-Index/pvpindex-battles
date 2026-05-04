package com.pvpindex.bungeecord.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import com.pvpindex.bungeecord.challenge.BungeePendingChallenge;
import com.pvpindex.network.NetworkMessage;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.PlayerRegistry;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class BungeeProxyMessageHandler implements Listener {

    private static final long CHALLENGE_TIMEOUT_SECONDS = 30;

    private final PvPIndexBungeePlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final Map<UUID, BungeePendingChallenge> pendingChallenges = new ConcurrentHashMap<>();

    public BungeeProxyMessageHandler(PvPIndexBungeePlugin plugin, ObjectMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();

        plugin.getProxy().getScheduler().schedule(plugin,
                this::expireStaleChallenge, 5, 5, TimeUnit.SECONDS);

        registerCrossProxyHandlers();
    }

    private void registerCrossProxyHandlers() {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        router.addHandler(NetworkMessageType.CHALLENGE_SEND, this::handleCrossProxyChallengeReceive);
        router.addHandler(NetworkMessageType.CHALLENGE_ACCEPT, this::handleCrossProxyChallengeAcceptReceive);
        router.addHandler(NetworkMessageType.CHALLENGE_DENY, this::handleCrossProxyChallengeDeclineReceive);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!PluginChannel.PROXY.equals(event.getTag())) return;
        if (!(event.getSender() instanceof Server backend)) return;

        event.setCancelled(true);

        String senderServer = backend.getInfo().getName();
        if (!plugin.config().isMonitored(senderServer)) return;

        BattleMessage msg;
        try {
            msg = BattleMessage.decode(mapper, event.getData());
        } catch (IOException e) {
            logger.warning("[BungeeMessageHandler] Failed to decode from " + senderServer + ": " + e.getMessage());
            return;
        }

        if (!msg.isValid(plugin.config().paperSecret())) {
            logger.warning("[BungeeMessageHandler] Rejected message from '" + senderServer + "' — invalid secret.");
            return;
        }

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] " + msg.type() + " from " + senderServer);
        }

        switch (msg.type()) {
            case CHALLENGE_SEND -> handleChallengeSend(senderServer, msg);
            case CHALLENGE_ACCEPT -> handleChallengeAccept(msg);
            case CHALLENGE_DECLINE -> handleChallengeDecline(msg);
            case BATTLE_START, BATTLE_END, PLAYER_ENTER_BATTLE, PLAYER_LEAVE_BATTLE, HEARTBEAT -> {
                if (plugin.config().debug()) {
                    logger.info("[BungeeMessageHandler] Processed " + msg.type() + " from " + senderServer);
                }
            }
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Challenge handlers (Paper → BungeeCord → Paper)
    // -------------------------------------------------------------------------

    private void handleChallengeSend(String senderServer, BattleMessage msg) {
        String challengerName = (String) msg.data().get("challengerName");
        String targetName = (String) msg.data().get("targetName");
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String modeId = (String) msg.data().get("modeId");

        if (challengerName == null || targetName == null || challengerUuidStr == null) return;

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

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetName);
        if (target != null && target.getServer() != null) {
            handleLocalChallenge(senderServer, challengeId, challengerUuid, challengerName,
                    target, modeId);
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

        plugin.backendMessenger().sendChallengeRejected(senderServer, challengeId, "player_not_found");
    }

    private void handleLocalChallenge(String senderServer, UUID challengeId,
                                      UUID challengerUuid, String challengerName,
                                      ProxiedPlayer target, String modeId) {
        if (target.getServer() == null) {
            plugin.backendMessenger().sendChallengeRejected(
                    senderServer, challengeId, "player_not_connected");
            return;
        }

        String targetServer = target.getServer().getInfo().getName();

        BungeePendingChallenge pending = new BungeePendingChallenge(
                challengeId, challengerUuid, senderServer,
                target.getUniqueId(), targetServer, modeId, Instant.now());
        pendingChallenges.put(challengeId, pending);

        plugin.backendMessenger().sendChallengeForward(
                targetServer, challengeId, challengerName, challengerUuid,
                modeId, target.getUniqueId());

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Challenge " + challengeId
                    + " from " + challengerName + "@" + senderServer
                    + " -> " + target.getName() + "@" + targetServer
                    + " mode=" + modeId);
        }
    }

    private void handleCrossProxyChallenge(String senderServer, UUID challengeId,
                                           UUID challengerUuid, String challengerName,
                                           PlayerRegistry.PlayerLocation targetLoc, String modeId) {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return;

        BungeePendingChallenge pending = new BungeePendingChallenge(
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
            logger.info("[BungeeMessageHandler] Cross-proxy challenge " + challengeId
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

        BungeePendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) {
            if (plugin.config().debug()) {
                logger.info("[BungeeMessageHandler] CHALLENGE_ACCEPT for unknown/expired challenge " + challengeId);
            }
            return;
        }

        if (pending.isCrossProxyReceiver()) {
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(pending.originProxy(), NetworkMessageType.CHALLENGE_ACCEPT, Map.of(
                        "challengeId", challengeId.toString(),
                        "accepterUuid", pending.targetUuid().toString(),
                        "targetServer", pending.targetServer(),
                        "originProxy", pending.originProxy(),
                        "originServer", pending.challengerServer(),
                        "challengerUuid", pending.challengerUuid().toString(),
                        "modeId", pending.modeId() != null ? pending.modeId() : ""
                ));
            }
            if (plugin.config().debug()) {
                logger.info("[BungeeMessageHandler] Challenge " + challengeId
                        + " ACCEPTED on receiver proxy, forwarding to origin " + pending.originProxy());
            }
            return;
        }

        if (pending.isCrossProxyOrigin()) {
            String targetProxy = pending.crossProxyTargetId();
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

            ProxiedPlayer targetPlayer = plugin.getProxy().getPlayer(pending.targetUuid());
            ServerInfo challengerServer = plugin.getProxy().getServerInfo(pending.challengerServer());
            if (targetPlayer != null && challengerServer != null) {
                targetPlayer.connect(challengerServer);
                if (plugin.config().debug()) {
                    logger.info("[BungeeMessageHandler] Transferring target "
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
            logger.info("[BungeeMessageHandler] Challenge " + challengeId
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

        BungeePendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) return;

        if (pending.isCrossProxyReceiver()) {
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(pending.originProxy(), NetworkMessageType.CHALLENGE_DENY, Map.of(
                        "challengeId", challengeId.toString(),
                        "reason", "declined"
                ));
            }
            if (plugin.config().debug()) {
                logger.info("[BungeeMessageHandler] Challenge " + challengeId
                        + " DECLINED on receiver proxy, forwarding to origin " + pending.originProxy());
            }
            return;
        }

        if (pending.isCrossProxyOrigin()) {
            String targetProxy = pending.crossProxyTargetId();
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
            logger.info("[BungeeMessageHandler] Challenge " + challengeId + " DECLINED");
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
        UUID challengerUuid = UUID.fromString(challengerUuidStr);

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetUuid);
        if (target == null || target.getServer() == null) {
            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                router.sendToProxy(originProxy, NetworkMessageType.CHALLENGE_DENY, Map.of(
                        "challengeId", challengeIdStr,
                        "reason", "player_not_found"
                ));
            }
            return;
        }

        String targetServer = target.getServer().getInfo().getName();

        BungeePendingChallenge pending = new BungeePendingChallenge(
                challengeId, challengerUuid, originServer,
                targetUuid, targetServer, modeId,
                Instant.now(), originProxy);
        pendingChallenges.put(challengeId, pending);

        plugin.backendMessenger().sendChallengeForward(
                targetServer, challengeId, challengerName,
                challengerUuid, modeId, targetUuid);

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Cross-proxy challenge " + challengeId
                    + " received from " + originProxy + ", stored pending and forwarded to "
                    + targetServer);
        }
    }

    private void handleCrossProxyChallengeAcceptReceive(NetworkMessage msg) {
        String challengeIdStr = msg.payloadString("challengeId");
        String challengerUuidStr = msg.payloadString("challengerUuid");
        String modeId = msg.payloadString("modeId");

        if (challengeIdStr == null || challengerUuidStr == null) return;

        UUID challengeId = UUID.fromString(challengeIdStr);

        BungeePendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) {
            if (plugin.config().debug()) {
                logger.info("[BungeeMessageHandler] Cross-proxy CHALLENGE_ACCEPT for unknown/expired "
                        + challengeId);
            }
            return;
        }

        plugin.backendMessenger().sendChallengeConfirmed(
                pending.challengerServer(), challengeId,
                pending.challengerUuid(), pending.targetUuid(),
                pending.modeId());

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Cross-proxy challenge " + challengeId
                    + " ACCEPTED, confirmed to " + pending.challengerServer());
        }
    }

    private void handleCrossProxyChallengeDeclineReceive(NetworkMessage msg) {
        String challengeIdStr = msg.payloadString("challengeId");
        if (challengeIdStr == null) return;

        UUID challengeId = UUID.fromString(challengeIdStr);
        BungeePendingChallenge pending = pendingChallenges.remove(challengeId);
        if (pending == null) return;

        String reason = msg.payloadString("reason");
        String rejectReason = reason != null ? reason : "declined";

        if (pending.isCrossProxyOrigin()) {
            plugin.backendMessenger().sendChallengeRejected(
                    pending.challengerServer(), challengeId, rejectReason);
        } else if (pending.isCrossProxyReceiver()) {
            plugin.backendMessenger().sendChallengeRejected(
                    pending.targetServer(), challengeId, rejectReason);
        } else {
            plugin.backendMessenger().sendChallengeRejected(
                    pending.challengerServer(), challengeId, rejectReason);
        }

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Cross-proxy challenge " + challengeId
                    + " DENIED via Redis (reason=" + rejectReason + ")");
        }
    }

    private void expireStaleChallenge() {
        Instant cutoff = Instant.now().minusSeconds(CHALLENGE_TIMEOUT_SECONDS);
        Iterator<Map.Entry<UUID, BungeePendingChallenge>> it = pendingChallenges.entrySet().iterator();
        while (it.hasNext()) {
            BungeePendingChallenge p = it.next().getValue();
            if (p.createdAt().isBefore(cutoff)) {
                it.remove();

                if (p.isCrossProxyReceiver()) {
                    NetworkRouter router = plugin.networkRouter();
                    if (router != null) {
                        router.sendToProxy(p.originProxy(), NetworkMessageType.CHALLENGE_DENY, Map.of(
                                "challengeId", p.challengeId().toString(),
                                "reason", "timeout"
                        ));
                    }
                } else if (p.isCrossProxyOrigin()) {
                    plugin.backendMessenger().sendChallengeRejected(
                            p.challengerServer(), p.challengeId(), "timeout");
                    NetworkRouter router = plugin.networkRouter();
                    if (router != null) {
                        String targetProxy = p.crossProxyTargetId();
                        router.sendToProxy(targetProxy, NetworkMessageType.CHALLENGE_DENY, Map.of(
                                "challengeId", p.challengeId().toString(),
                                "reason", "timeout"
                        ));
                    }
                } else {
                    plugin.backendMessenger().sendChallengeRejected(
                            p.challengerServer(), p.challengeId(), "timeout");
                    if (!p.challengerServer().equals(p.targetServer())) {
                        plugin.backendMessenger().sendChallengeRejected(
                                p.targetServer(), p.challengeId(), "timeout");
                    }
                }

                if (plugin.config().debug()) {
                    logger.info("[BungeeMessageHandler] Challenge " + p.challengeId() + " EXPIRED"
                            + (p.isCrossProxyReceiver() ? " (receiver-side, notified " + p.originProxy() + ")"
                            : p.isCrossProxyOrigin() ? " (origin-side, notified remote)"
                            : ""));
                }
            }
        }
    }
}
