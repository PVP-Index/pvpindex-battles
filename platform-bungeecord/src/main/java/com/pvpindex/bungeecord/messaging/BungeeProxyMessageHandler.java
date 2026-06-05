package com.pvpindex.bungeecord.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * BungeeCord proxy message handler bridging plugin messaging (Paper backends)
 * and Redis (lobby servers). Tracks pending legacy challenges so that
 * accepts/declines from lobby servers (arriving via Redis) can be forwarded
 * to the correct backend via plugin messaging.
 */
public final class BungeeProxyMessageHandler implements Listener {

    private final PvPIndexBungeePlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;

    private record PendingLegacyChallenge(
            String senderServer, UUID challengeId, UUID challengerUuid,
            String challengerName, UUID targetUuid, String targetServer,
            String modeId, Instant createdAt
    ) {}

    private final ConcurrentHashMap<UUID, PendingLegacyChallenge> pendingChallenges =
            new ConcurrentHashMap<>();

    public BungeeProxyMessageHandler(PvPIndexBungeePlugin plugin, ObjectMapper mapper) {
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
            ProxiedPlayer player = plugin.getProxy().getPlayer(playerId);
            ServerInfo server = plugin.getProxy().getServerInfo(targetServer);

            if (player != null && server != null) {
                plugin.markChallengeTransfer(playerId);
                player.connect(server);
                logger.info("[PvPIndex Transfer] Transferring " + player.getName()
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
                        "reason", player == null ? "player_not_found" : "server_not_found",
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
                    plc.senderServer(), plc.challengeId(), plc.challengerUuid(),
                    targetUuid, plc.modeId(), targetUuid);

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
                    + " - forwarding REJECTED to backend '" + plc.senderServer() + "'");

            plugin.backendMessenger().sendChallengeRejected(
                    plc.senderServer(), plc.challengeId(), "declined");

            if (plc.targetServer() != null && !plc.targetServer().equals(plc.senderServer())) {
                plugin.backendMessenger().sendChallengeCleanup(plc.targetServer(), challengeId);
            }
        });
    }

    private void scheduleExpiry() {
        plugin.getProxy().getScheduler().schedule(plugin,
                () -> {
                    Instant cutoff = Instant.now().minusSeconds(120);
                    pendingChallenges.values().removeIf(plc -> plc.createdAt().isBefore(cutoff));
                },
                30, 30, TimeUnit.SECONDS);
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
            logger.warning("[BungeeMessageHandler] Rejected message from '" + senderServer + "' - invalid secret.");
            return;
        }

        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] " + msg.type() + " from " + senderServer);
        }

        switch (msg.type()) {
            case BATTLE_END -> handleBattleEnd(msg);
            case BATTLE_START, PLAYER_ENTER_BATTLE, PLAYER_LEAVE_BATTLE, HEARTBEAT -> {
                if (plugin.config().debug()) {
                    logger.info("[BungeeMessageHandler] Processed " + msg.type() + " from " + senderServer);
                }
            }
            case CHALLENGE_SEND -> handleLegacyChallengeSend(senderServer, msg);
            case CHALLENGE_ACCEPT -> handleLegacyChallengeAccept(senderServer, msg);
            case CHALLENGE_DECLINE -> handleLegacyChallengeDecline(senderServer, msg);
            default -> {}
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBattleEnd(BattleMessage msg) {
        List<String> participantStrs = (List<String>) msg.data().get("participants");
        if (participantStrs == null || participantStrs.isEmpty()) return;

        for (String uuidStr : participantStrs) {
            UUID playerUuid;
            try { playerUuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            String originServer = plugin.removePlayerOriginServer(playerUuid);
            if (originServer == null) continue;

            ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
            if (player == null || !player.isConnected()) continue;

            ServerInfo target = plugin.getProxy().getServerInfo(originServer);
            if (target == null) {
                logger.warning("[PvPIndex Return] Origin server '" + originServer
                        + "' not found for " + playerUuid + ". cannot return.");
                continue;
            }

            String currentServer = player.getServer() != null
                    ? player.getServer().getInfo().getName() : "unknown";
            if (currentServer.equals(originServer)) continue;

            plugin.markChallengeTransfer(playerUuid);
            plugin.getProxy().getScheduler().schedule(plugin, () -> {
                if (player.isConnected()) {
                    player.connect(target);
                    logger.info("[PvPIndex Return] Battle ended. returning "
                            + player.getName() + " from " + currentServer
                            + " to origin '" + originServer + "'");
                }
            }, 2, TimeUnit.SECONDS);
        }
    }

    private void transferTargetToBackend(UUID targetUuid, String backendServer, UUID challengeId) {
        ProxiedPlayer target = plugin.getProxy().getPlayer(targetUuid);
        ServerInfo server = plugin.getProxy().getServerInfo(backendServer);

        if (target != null && server != null) {
            String currentServer = target.getServer() != null
                    ? target.getServer().getInfo().getName() : "unknown";
            if (!currentServer.equals(backendServer)) {
                plugin.setPlayerOriginServer(targetUuid, currentServer);
                plugin.markChallengeTransfer(targetUuid);
                target.connect(server);
                logger.info("[PvPIndex Transfer] Challenge " + challengeId
                        + " - transferring " + target.getName()
                        + " from " + currentServer + " to " + backendServer);
            }
        } else {
            logger.warning("[PvPIndex Transfer] Cannot transfer target " + targetUuid
                    + " to " + backendServer + " - player="
                    + (target != null ? "found" : "not_found")
                    + " server=" + (server != null ? "found" : "not_found"));
        }
    }

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

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetName);
        if (target != null && target.getServer() != null) {
            targetServer = target.getServer().getInfo().getName();
            targetUuid = target.getUniqueId();
        } else {
            com.pvpindex.network.NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                var location = router.playerRegistry().findPlayerByName(targetName);
                if (location.isPresent()) {
                    targetUuid = location.get().playerId();
                    targetServer = location.get().serverName();
                    logger.info("[BungeeMessageHandler] Cross-proxy lookup: " + targetName
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
            logger.info("[BungeeMessageHandler] Tracked challenge " + challengeId
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
            logger.warning("[BungeeMessageHandler] CHALLENGE_ACCEPT for unknown challenge " + challengeId);
            return;
        }

        UUID accepterUuid = null;
        if (accepterUuidStr != null) {
            try { accepterUuid = UUID.fromString(accepterUuidStr); }
            catch (IllegalArgumentException ignored) {}
        }

        logger.info("[BungeeMessageHandler] Forwarding CHALLENGE_CONFIRMED to '" + plc.senderServer()
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
            logger.warning("[BungeeMessageHandler] CHALLENGE_DECLINE for unknown challenge " + challengeId);
            return;
        }

        logger.info("[BungeeMessageHandler] Forwarding CHALLENGE_REJECTED to '" + plc.senderServer()
                + "' for challenge " + challengeId);

        plugin.backendMessenger().sendChallengeRejected(
                plc.senderServer(), plc.challengeId(), "declined");

        if (!senderServer.equals(plc.senderServer())) {
            plugin.backendMessenger().sendChallengeCleanup(senderServer, challengeId);
        }
    }
}
