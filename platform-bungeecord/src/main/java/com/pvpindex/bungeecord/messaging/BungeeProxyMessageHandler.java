package com.pvpindex.bungeecord.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.common.messaging.BattleMessage;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public final class BungeeProxyMessageHandler implements Listener {

    private final PvPIndexBungeePlugin plugin;
    private final ObjectMapper mapper;
    private final Logger logger;

    public BungeeProxyMessageHandler(PvPIndexBungeePlugin plugin, ObjectMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.logger = plugin.getLogger();
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

    private void handleChallengeSend(String senderServer, BattleMessage msg) {
        String challengerName = (String) msg.data().get("challengerName");
        String targetName = (String) msg.data().get("targetName");
        String challengeIdStr = (String) msg.data().get("challengeId");
        String challengerUuidStr = (String) msg.data().get("challengerUuid");
        String modeId = (String) msg.data().get("modeId");

        if (challengerName == null || targetName == null || challengerUuidStr == null) return;

        UUID challengeId = challengeIdStr != null ? UUID.fromString(challengeIdStr) : UUID.randomUUID();

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetName);
        if (target == null || target.getServer() == null) {
            plugin.backendMessenger().sendChallengeRejected(senderServer, challengeId, "player_not_found");
            return;
        }

        String targetServer = target.getServer().getInfo().getName();
        plugin.backendMessenger().sendChallengeForward(
                targetServer, challengeId, challengerName,
                UUID.fromString(challengerUuidStr), modeId, target.getUniqueId());
    }

    private void handleChallengeAccept(BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;
        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Challenge " + challengeIdStr + " accepted.");
        }
    }

    private void handleChallengeDecline(BattleMessage msg) {
        String challengeIdStr = (String) msg.data().get("challengeId");
        if (challengeIdStr == null) return;
        if (plugin.config().debug()) {
            logger.info("[BungeeMessageHandler] Challenge " + challengeIdStr + " declined.");
        }
    }
}
