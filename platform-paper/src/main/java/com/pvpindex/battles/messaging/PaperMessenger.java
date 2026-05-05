package com.pvpindex.battles.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.MessageType;
import com.pvpindex.battles.common.messaging.PluginChannel;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;

/**
 * Sends {@link BattleMessage} payloads from the Paper backend to the Velocity
 * proxy over the {@link PluginChannel#PROXY} channel.
 *
 * <p>Plugin messaging in Bukkit requires a player as the "conduit": the message
 * is dispatched through whichever online player is returned by
 * {@link #findConduit()}. If no player is online the message is silently
 * dropped (the proxy is not reachable without a connected player anyway).</p>
 *
 * <p>Outgoing messages carry the configurable {@link #secret} so the Velocity
 * side can reject forged payloads from untrusted backends.</p>
 */
public final class PaperMessenger {

    private final Plugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final String secret;
    private boolean registered = false;

    public PaperMessenger(Plugin plugin, ObjectMapper mapper, String secret) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();
        this.secret = secret == null ? "" : secret;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Register the outgoing plugin channel. Call this once on plugin enable. */
    public void register() {
        if (registered) return;
        Messenger bm = plugin.getServer().getMessenger();
        if (!bm.isOutgoingChannelRegistered(plugin, PluginChannel.PROXY)) {
            bm.registerOutgoingPluginChannel(plugin, PluginChannel.PROXY);
        }
        registered = true;
        logger.info("[PaperMessenger] Outgoing channel '" + PluginChannel.PROXY + "' registered.");
    }

    /** Unregister the outgoing plugin channel. Call this on plugin disable. */
    public void unregister() {
        if (!registered) return;
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, PluginChannel.PROXY);
        registered = false;
    }

    // -------------------------------------------------------------------------
    // Message senders
    // -------------------------------------------------------------------------

    /** Notify Velocity that a battle has started on this backend. */
    public void sendBattleStart(BattleSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("battleUuid", session.getUuid().toString());
        data.put("serverId", session.getServerId());
        data.put("participants", session.getParticipants().stream()
                .map(p -> Map.of("uuid", p.getUuid().toString(), "username", p.getMinecraftUsername()))
                .toList());
        send(MessageType.BATTLE_START, data);
    }

    /** Notify the proxy that a battle has ended. */
    public void sendBattleEnd(BattleSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("battleUuid", session.getUuid().toString());
        data.put("status", session.getStatus().name());
        data.put("winners", session.getWinners().stream().map(UUID::toString).toList());
        data.put("participants", session.getParticipants().stream()
                .map(p -> p.getUuid().toString()).toList());
        send(MessageType.BATTLE_END, data);
    }

    /** Notify Velocity that a player has entered a battle. */
    public void sendPlayerEnterBattle(UUID playerUuid, UUID battleUuid) {
        send(MessageType.PLAYER_ENTER_BATTLE,
                Map.of("playerUuid", playerUuid.toString(), "battleUuid", battleUuid.toString()));
    }

    /** Notify Velocity that a player has left a battle. */
    public void sendPlayerLeaveBattle(UUID playerUuid, UUID battleUuid, String reason) {
        send(MessageType.PLAYER_LEAVE_BATTLE,
                Map.of("playerUuid", playerUuid.toString(),
                        "battleUuid", battleUuid.toString(),
                        "reason", reason));
    }

    /** Send a periodic heartbeat so Velocity knows this backend is alive. */
    public void sendHeartbeat(String serverId, int activeBattleCount) {
        send(MessageType.HEARTBEAT,
                Map.of("serverId", serverId,
                        "activeBattleCount", activeBattleCount,
                        "timestampEpochMs", System.currentTimeMillis()));
    }

    // -------------------------------------------------------------------------
    // Challenge senders (Paper → Velocity)
    // -------------------------------------------------------------------------

    /** Send a challenge request to Velocity for routing to the target's server. */
    public void sendChallengeSend(UUID challengeId, UUID challengerUuid, String challengerName,
            String targetName, String modeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("challengeId", challengeId.toString());
        data.put("challengerUuid", challengerUuid.toString());
        data.put("challengerName", challengerName);
        data.put("targetName", targetName);
        data.put("modeId", modeId != null ? modeId : "");
        send(MessageType.CHALLENGE_SEND, data);
    }

    /** Tell Velocity this backend's player accepted a challenge. */
    public void sendChallengeAccept(UUID challengeId, UUID accepterUuid) {
        send(MessageType.CHALLENGE_ACCEPT,
                Map.of("challengeId", challengeId.toString(),
                        "accepterUuid", accepterUuid.toString()));
    }

    /** Tell Velocity this backend's player declined a challenge. */
    public void sendChallengeDecline(UUID challengeId, UUID declinerUuid) {
        send(MessageType.CHALLENGE_DECLINE,
                Map.of("challengeId", challengeId.toString(),
                        "declinerUuid", declinerUuid.toString()));
    }

    // -------------------------------------------------------------------------
    // Internal dispatch
    // -------------------------------------------------------------------------

    private void send(MessageType type, Map<String, Object> data) {
        if (!registered) return;
        Optional<Player> conduit = findConduit();
        if (conduit.isEmpty()) {
            logger.warning("[PaperMessenger] No online player to send " + type + " — message dropped. "
                    + "At least one player must be online for plugin messaging to work.");
            return;
        }
        try {
            byte[] bytes = BattleMessage.encode(mapper, type, secret, data);
            conduit.get().sendPluginMessage(plugin, PluginChannel.PROXY, bytes);
        } catch (IOException e) {
            logger.warning("[PaperMessenger] Failed to encode " + type + ": " + e.getMessage());
        }
    }

    /**
     * Returns any online player to use as the plugin-messaging conduit.
     * Plugin messages in Bukkit require a connected player — the proxy is only
     * reachable when at least one player bridges the connection.
     */
    private Optional<Player> findConduit() {
        return Bukkit.getOnlinePlayers().stream().findFirst().map(p -> p);
    }
}
