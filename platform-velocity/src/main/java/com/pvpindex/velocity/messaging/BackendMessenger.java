package com.pvpindex.velocity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.MessageType;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Sends {@link BattleMessage} payloads from the Velocity proxy to Paper
 * backend servers over the {@link PluginChannel#PROXY} channel.
 *
 * <p>Velocity plugin messaging requires routing messages through a connected
 * player — the same limitation as the Paper side. This class picks any player
 * currently on the target server as the conduit. If the server has no
 * connected players the message is silently dropped (the server is effectively
 * unreachable for plugin messaging purposes).</p>
 */
public final class BackendMessenger {

    static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannel.PROXY);

    private final ProxyServer server;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final String secret;

    public BackendMessenger(ProxyServer server, ObjectMapper mapper, Logger logger, String secret) {
        this.server = server;
        this.mapper = mapper;
        this.logger = logger;
        this.secret = secret == null ? "" : secret;
    }

    // -------------------------------------------------------------------------
    // Message senders (Velocity → Paper)
    // -------------------------------------------------------------------------

    /**
     * Tell a backend that a player in an active battle has switched servers.
     * The backend should cancel the battle for that player.
     */
    public void sendPlayerSwitchedServer(String targetServer, UUID playerUuid,
            String fromServer, String toServer, UUID battleUuid) {
        Map<String, Object> data = Map.of(
                "playerUuid", playerUuid.toString(),
                "fromServer", fromServer,
                "toServer", toServer,
                "battleUuid", battleUuid.toString());
        send(targetServer, MessageType.PLAYER_SWITCHED_SERVER, data);
    }

    /**
     * Instruct a backend to cancel a specific battle (e.g. arena server went down).
     */
    public void sendCancelBattle(String targetServer, UUID battleUuid, String reason) {
        send(targetServer, MessageType.CANCEL_BATTLE,
                Map.of("battleUuid", battleUuid.toString(), "reason", reason));
    }

    /**
     * Tell a backend where a specific player currently is.
     */
    public void sendPlayerServerInfo(String targetServer, UUID playerUuid, String serverName) {
        send(targetServer, MessageType.PLAYER_SERVER_INFO,
                Map.of("playerUuid", playerUuid.toString(), "serverName", serverName));
    }

    /**
     * Broadcast the list of all registered server names to a backend.
     */
    public void sendServerList(String targetServer) {
        List<String> servers = server.getAllServers().stream()
                .map(s -> s.getServerInfo().getName())
                .collect(Collectors.toList());
        send(targetServer, MessageType.SERVER_LIST, Map.of("servers", servers));
    }

    // -------------------------------------------------------------------------
    // Network awareness (Velocity → Paper)
    // -------------------------------------------------------------------------

    /**
     * Broadcast the list of all online player names to every backend server
     * that currently has at least one connected player.
     */
    public void broadcastNetworkPlayerList() {
        broadcastNetworkPlayerList(List.of());
    }

    /**
     * Broadcast the combined list of local + remote online players to every
     * backend server that currently has at least one connected player.
     *
     * @param remoteEntries additional player entries from other proxies
     *                      (obtained via the cross-proxy network layer)
     */
    public void broadcastNetworkPlayerList(List<Map<String, String>> remoteEntries) {
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> playerEntries = new ArrayList<>();

        for (Player p : server.getAllPlayers()) {
            String uuid = p.getUniqueId().toString();
            seen.add(uuid);
            String srv = p.getCurrentServer()
                    .map(c -> c.getServerInfo().getName())
                    .orElse("unknown");
            playerEntries.add(Map.of(
                    "name", p.getUsername(),
                    "uuid", uuid,
                    "server", srv));
        }

        for (Map<String, String> remote : remoteEntries) {
            if (seen.add(remote.get("uuid"))) {
                playerEntries.add(remote);
            }
        }

        Map<String, Object> data = Map.of("players", playerEntries);

        for (RegisteredServer rs : server.getAllServers()) {
            String name = rs.getServerInfo().getName();
            if (!rs.getPlayersConnected().isEmpty()) {
                send(name, MessageType.NETWORK_PLAYER_LIST, data);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Challenge senders (Velocity → Paper)
    // -------------------------------------------------------------------------

    /** Forward a challenge to the target player's backend. */
    public void sendChallengeForward(String targetServer, UUID challengeId,
            String challengerName, UUID challengerUuid, String modeId, UUID targetUuid) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerName", challengerName);
        data.put("challengerUuid", challengerUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        data.put("targetUuid", targetUuid.toString());
        send(targetServer, MessageType.CHALLENGE_FORWARD, data);
    }

    /**
     * Notify a backend that a challenge was accepted and battle should start.
     * Includes full participant data so the receiving server can start the battle
     * even if it only had a partial local record.
     */
    public void sendChallengeConfirmed(String targetServer, UUID challengeId,
            UUID challengerUuid, UUID targetUuid, String modeId) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerUuid", challengerUuid.toString());
        data.put("targetUuid", targetUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        send(targetServer, MessageType.CHALLENGE_CONFIRMED, data);
    }

    /** Notify a backend that a challenge was declined or timed out. */
    public void sendChallengeRejected(String targetServer, UUID challengeId, String reason) {
        send(targetServer, MessageType.CHALLENGE_REJECTED,
                Map.of("challengeId", challengeId.toString(), "reason", reason));
    }

    /** Notify the target's backend to clean up a pending challenge (no battle start). */
    public void sendChallengeCleanup(String targetServer, UUID challengeId) {
        send(targetServer, MessageType.CHALLENGE_CLEANUP,
                Map.of("challengeId", challengeId.toString()));
    }

    // -------------------------------------------------------------------------
    // Internal dispatch
    // -------------------------------------------------------------------------

    private void send(String targetServerName, MessageType type, Map<String, Object> data) {
        send(targetServerName, type, data, null);
    }

    private void send(String targetServerName, MessageType type, Map<String, Object> data, UUID excludeUuid) {
        Optional<RegisteredServer> registeredServer = server.getServer(targetServerName);
        if (registeredServer.isEmpty()) {
            logger.warning("[BackendMessenger] Server '" + targetServerName
                    + "' not registered on this proxy — dropping " + type
                    + ". Check your Velocity server configuration.");
            return;
        }

        var players = registeredServer.get().getPlayersConnected();
        Optional<Player> conduit;
        if (excludeUuid != null) {
            conduit = players.stream()
                    .filter(p -> !p.getUniqueId().equals(excludeUuid))
                    .findFirst();
            if (conduit.isEmpty()) {
                conduit = players.stream().findFirst();
            }
        } else {
            conduit = players.stream().findFirst();
        }
        if (conduit.isEmpty()) {
            logger.warning("[BackendMessenger] No players on '" + targetServerName
                    + "' — dropping " + type
                    + ". Plugin messaging requires at least one player on the target server.");
            return;
        }

        var serverConn = conduit.get().getCurrentServer();
        if (serverConn.isEmpty()) {
            logger.warning("[BackendMessenger] Conduit player on '" + targetServerName
                    + "' has no active server connection — dropping " + type);
            return;
        }

        try {
            byte[] bytes = BattleMessage.encode(mapper, type, secret, data);
            serverConn.get().sendPluginMessage(CHANNEL, bytes);
        } catch (IOException e) {
            logger.warning("[BackendMessenger] Failed to encode " + type + ": " + e.getMessage());
        }
    }

    /**
     * Overload that allows excluding a specific player UUID from conduit selection.
     * Used when the player being transferred should not be used as the message carrier.
     */
    public void sendChallengeConfirmedExcluding(String targetServer, UUID challengeId,
            UUID challengerUuid, UUID targetUuid, String modeId, UUID excludeUuid) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerUuid", challengerUuid.toString());
        data.put("targetUuid", targetUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        send(targetServer, MessageType.CHALLENGE_CONFIRMED, data, excludeUuid);
    }
}
