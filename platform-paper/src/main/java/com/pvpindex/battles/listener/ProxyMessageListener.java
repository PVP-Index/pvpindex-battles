package com.pvpindex.battles.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.battles.messaging.NetworkPlayerCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives {@link BattleMessage} payloads from the Velocity proxy plugin over
 * the {@link PluginChannel#PROXY} channel and acts on them server-side.
 *
 * <p>Incoming messages are validated against the configured {@code secret}
 * before any action is taken. Unknown or invalid messages are logged at
 * warning level and discarded.</p>
 */
public final class ProxyMessageListener implements PluginMessageListener {

    private final Plugin plugin;
    private final BattleService battleService;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final String expectedSecret;
    private final boolean debug;
    private ChallengeManager challengeManager;
    private NetworkPlayerCache networkPlayerCache;

    public ProxyMessageListener(Plugin plugin, BattleService battleService,
            ObjectMapper mapper, String expectedSecret, boolean debug) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.mapper = mapper;
        this.logger = plugin.getLogger();
        this.expectedSecret = expectedSecret;
        this.debug = debug;
    }

    public void setChallengeManager(ChallengeManager challengeManager) {
        this.challengeManager = challengeManager;
    }

    public void setNetworkPlayerCache(NetworkPlayerCache cache) {
        this.networkPlayerCache = cache;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Register the incoming plugin channel. Call once on plugin enable. */
    public void register() {
        Messenger bm = plugin.getServer().getMessenger();
        if (!bm.isIncomingChannelRegistered(plugin, PluginChannel.PROXY)) {
            bm.registerIncomingPluginChannel(plugin, PluginChannel.PROXY, this);
        }
        logger.info("[ProxyMessageListener] Incoming channel '" + PluginChannel.PROXY + "' registered.");
    }

    /** Unregister the incoming plugin channel. Call on plugin disable. */
    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, PluginChannel.PROXY, this);
    }

    // -------------------------------------------------------------------------
    // PluginMessageListener
    // -------------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginChannel.PROXY.equals(channel)) return;

        BattleMessage msg;
        try {
            msg = BattleMessage.decode(mapper, message);
        } catch (IOException e) {
            logger.warning("[ProxyMessage] Failed to decode message: " + e.getMessage());
            return;
        }

        if (!msg.isValid(expectedSecret)) {
            logger.warning("[ProxyMessage] Rejected message with invalid secret (type=" + msg.type() + ").");
            return;
        }

        switch (msg.type()) {
            case PLAYER_SWITCHED_SERVER -> handlePlayerSwitchedServer(msg);
            case CANCEL_BATTLE          -> handleCancelBattle(msg);
            case SERVER_LIST            -> handleServerList(msg);
            case PLAYER_SERVER_INFO     -> handlePlayerServerInfo(msg);
            case CHALLENGE_FORWARD      -> handleChallengeForward(msg);
            case CHALLENGE_CONFIRMED    -> handleChallengeConfirmed(msg);
            case CHALLENGE_REJECTED     -> handleChallengeRejected(msg);
            case CHALLENGE_CLEANUP      -> handleChallengeCleanup(msg);
            case NETWORK_PLAYER_LIST    -> handleNetworkPlayerList(msg);
            default -> {
                if (debug) logger.info("[ProxyMessage] Unhandled message type: " + msg.type());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * A player in an active battle has server-switched. Cancel their battle
     * on this backend — they can no longer fight here.
     */
    private void handlePlayerSwitchedServer(BattleMessage msg) {
        String playerUuidStr = (String) msg.data().get("playerUuid");
        String battleUuidStr = (String) msg.data().get("battleUuid");
        String toServer = (String) msg.data().get("toServer");

        if (playerUuidStr == null || battleUuidStr == null) {
            logger.warning("[ProxyMessage] PLAYER_SWITCHED_SERVER missing required fields.");
            return;
        }

        UUID battleUuid;
        try {
            battleUuid = UUID.fromString(battleUuidStr);
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] PLAYER_SWITCHED_SERVER invalid battleUuid: " + battleUuidStr);
            return;
        }

        Optional<BattleSession> session = battleService.find(battleUuid);
        if (session.isEmpty()) {
            if (debug) logger.info("[ProxyMessage] PLAYER_SWITCHED_SERVER: battle " + battleUuid + " not found (already ended?).");
            return;
        }

        logger.warning("[ProxyMessage] Player " + playerUuidStr
                + " switched to server '" + (toServer != null ? toServer : "?")
                + "' mid-battle " + battleUuid + " — cancelling battle.");
        battleService.cancelBattle(battleUuid);
    }

    /**
     * Velocity has instructed this backend to cancel a specific battle.
     */
    private void handleCancelBattle(BattleMessage msg) {
        String battleUuidStr = (String) msg.data().get("battleUuid");
        String reason = (String) msg.data().getOrDefault("reason", "proxy_cancel");

        if (battleUuidStr == null) {
            logger.warning("[ProxyMessage] CANCEL_BATTLE missing battleUuid.");
            return;
        }

        UUID battleUuid;
        try {
            battleUuid = UUID.fromString(battleUuidStr);
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] CANCEL_BATTLE invalid battleUuid: " + battleUuidStr);
            return;
        }

        if (battleService.find(battleUuid).isPresent()) {
            logger.info("[ProxyMessage] Velocity requested cancel of battle " + battleUuid + " (reason: " + reason + ").");
            battleService.cancelBattle(battleUuid);
        } else if (debug) {
            logger.info("[ProxyMessage] CANCEL_BATTLE: battle " + battleUuid + " not found (already done?).");
        }
    }

    /**
     * Velocity is broadcasting the list of registered backend server names.
     * Stored in session metadata for informational use.
     */
    private void handleServerList(BattleMessage msg) {
        if (debug) logger.info("[ProxyMessage] SERVER_LIST received: " + msg.data().get("servers"));
    }

    /**
     * Velocity is responding with a player's current server.
     */
    private void handlePlayerServerInfo(BattleMessage msg) {
        if (debug) {
            logger.info("[ProxyMessage] PLAYER_SERVER_INFO: player=" + msg.data().get("playerUuid")
                    + " server=" + msg.data().get("serverName"));
        }
    }

    // -------------------------------------------------------------------------
    // Challenge handlers (Velocity → Paper)
    // -------------------------------------------------------------------------

    private void handleChallengeForward(BattleMessage msg) {
        if (challengeManager == null) return;
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerName = (String) msg.data().get("challengerName");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String modeId = (String) msg.data().get("modeId");
        String targetUuidStr = (String) msg.data().get("targetUuid");

        if (challengeIdStr == null || challengerUuidStr == null || targetUuidStr == null) {
            logger.warning("[ProxyMessage] CHALLENGE_FORWARD missing required fields.");
            return;
        }

        try {
            UUID challengeId = UUID.fromString(challengeIdStr);
            UUID challengerUuid = UUID.fromString(challengerUuidStr);
            UUID targetUuid = UUID.fromString(targetUuidStr);
            String resolvedMode = (modeId != null && !modeId.isBlank()) ? modeId : null;
            challengeManager.handleIncomingChallenge(challengeId, challengerName,
                    challengerUuid, resolvedMode, targetUuid);
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] CHALLENGE_FORWARD invalid UUID: " + e.getMessage());
        }
    }

    private void handleChallengeConfirmed(BattleMessage msg) {
        if (challengeManager == null) return;
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String targetUuidStr = (String) msg.data().get("targetUuid");
        String modeId = (String) msg.data().get("modeId");

        if (challengeIdStr == null) return;
        try {
            UUID challengeId = UUID.fromString(challengeIdStr);
            UUID challengerUuid = challengerUuidStr != null ? UUID.fromString(challengerUuidStr) : null;
            UUID targetUuid = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;
            challengeManager.handleChallengeConfirmed(challengeId, challengerUuid, targetUuid, modeId);
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] CHALLENGE_CONFIRMED invalid UUID: " + e.getMessage());
        }
    }

    private void handleChallengeRejected(BattleMessage msg) {
        if (challengeManager == null) return;
        String challengeIdStr = (String) msg.data().get("challengeId");
        String reason = (String) msg.data().getOrDefault("reason", "unknown");
        if (challengeIdStr == null) return;
        try {
            challengeManager.handleChallengeRejected(UUID.fromString(challengeIdStr), reason);
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] CHALLENGE_REJECTED invalid UUID.");
        }
    }

    private void handleChallengeCleanup(BattleMessage msg) {
        if (challengeManager == null) return;
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;
        try {
            challengeManager.handleChallengeCleanup(UUID.fromString(challengeIdStr));
        } catch (IllegalArgumentException e) {
            logger.warning("[ProxyMessage] CHALLENGE_CLEANUP invalid UUID.");
        }
    }

    // -------------------------------------------------------------------------
    // Network awareness
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void handleNetworkPlayerList(BattleMessage msg) {
        if (networkPlayerCache == null) return;
        Object raw = msg.data().get("players");
        if (!(raw instanceof List<?> rawList)) return;

        List<NetworkPlayerCache.NetworkPlayer> parsed = new ArrayList<>();
        for (Object entry : rawList) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            String name = (String) map.get("name");
            String uuidStr = (String) map.get("uuid");
            String srv = (String) map.get("server");
            if (name == null || uuidStr == null) continue;
            try {
                parsed.add(new NetworkPlayerCache.NetworkPlayer(name, UUID.fromString(uuidStr), srv));
            } catch (IllegalArgumentException ignored) {}
        }
        networkPlayerCache.update(parsed);

        if (debug) {
            logger.info("[ProxyMessage] NETWORK_PLAYER_LIST updated: " + parsed.size() + " players");
        }
    }
}
