package com.pvpindex.bungeecord.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.MessageType;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class BungeeBackendMessenger {

    private final PvPIndexBungeePlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;

    public BungeeBackendMessenger(PvPIndexBungeePlugin plugin, ObjectMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();
    }

    public void sendPlayerSwitchedServer(String targetServer, UUID playerUuid,
                                         String fromServer, String toServer, UUID battleUuid) {
        send(targetServer, MessageType.PLAYER_SWITCHED_SERVER, Map.of(
                "playerUuid", playerUuid.toString(),
                "fromServer", fromServer,
                "toServer", toServer,
                "battleUuid", battleUuid.toString()));
    }

    public void sendChallengeForward(String targetServer, UUID challengeId,
                                     String challengerName, UUID challengerUuid,
                                     String modeId, UUID targetUuid) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerName", challengerName);
        data.put("challengerUuid", challengerUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        data.put("targetUuid", targetUuid.toString());
        send(targetServer, MessageType.CHALLENGE_FORWARD, data);
    }

    public void sendChallengeConfirmed(String targetServer, UUID challengeId,
                                       UUID challengerUuid, UUID targetUuid, String modeId) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerUuid", challengerUuid.toString());
        data.put("targetUuid", targetUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        send(targetServer, MessageType.CHALLENGE_CONFIRMED, data);
    }

    public void sendChallengeRejected(String targetServer, UUID challengeId, String reason) {
        send(targetServer, MessageType.CHALLENGE_REJECTED,
                Map.of("challengeId", challengeId.toString(), "reason", reason));
    }

    public void sendChallengeCleanup(String targetServer, UUID challengeId) {
        send(targetServer, MessageType.CHALLENGE_CLEANUP,
                Map.of("challengeId", challengeId.toString()));
    }

    public void broadcastNetworkPlayerList() {
        List<Map<String, String>> entries = plugin.getProxy().getPlayers().stream()
                .map(p -> {
                    String srv = p.getServer() != null ? p.getServer().getInfo().getName() : "unknown";
                    return Map.of("name", p.getName(), "uuid", p.getUniqueId().toString(), "server", srv);
                })
                .collect(Collectors.toList());

        Map<String, Object> data = Map.of("players", entries);

        for (var entry : plugin.getProxy().getServers().entrySet()) {
            ServerInfo info = entry.getValue();
            if (!info.getPlayers().isEmpty()) {
                send(entry.getKey(), MessageType.NETWORK_PLAYER_LIST, data);
            }
        }
    }

    public void sendChallengeConfirmedExcluding(String targetServer, UUID challengeId,
                                                UUID challengerUuid, UUID targetUuid,
                                                String modeId, UUID excludeUuid) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerUuid", challengerUuid.toString());
        data.put("targetUuid", targetUuid.toString());
        data.put("modeId", modeId != null ? modeId : "");
        send(targetServer, MessageType.CHALLENGE_CONFIRMED, data);
    }

    private void send(String targetServerName, MessageType type, Map<String, Object> data) {
        ServerInfo serverInfo = plugin.getProxy().getServerInfo(targetServerName);
        if (serverInfo == null) {
            logger.warning("[BungeeMessenger] Server '" + targetServerName + "' not found — dropping " + type);
            return;
        }

        if (serverInfo.getPlayers().isEmpty()) {
            logger.warning("[BungeeMessenger] No players on '" + targetServerName + "' — dropping " + type);
            return;
        }

        try {
            byte[] bytes = BattleMessage.encode(mapper, type, plugin.config().paperSecret(), data);
            serverInfo.sendData(PluginChannel.PROXY, bytes);
        } catch (IOException e) {
            logger.warning("[BungeeMessenger] Failed to encode " + type + ": " + e.getMessage());
        }
    }
}
