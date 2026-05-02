package com.pvpindex.velocity.listener;

import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.pvpindex.velocity.messaging.BackendMessenger;
import com.pvpindex.velocity.registry.BattleRegistry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;

import java.util.UUID;
import java.util.logging.Logger;

public final class ServerSwitchListener {

    private final PvPIndexVelocityPlugin plugin;
    private final Logger logger;

    public ServerSwitchListener(PvPIndexVelocityPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String targetServer = event.getServer().getServerInfo().getName();
        String previousServer = event.getPreviousServer()
                .map(s -> s.getServerInfo().getName())
                .orElse(null);

        if (previousServer == null) return;
        if (previousServer.equals(targetServer)) return;

        if (plugin.isChallengeTransfer(playerUuid)) {
            logger.info("[ServerSwitch] Player " + event.getPlayer().getUsername()
                    + " transferred for challenge from '" + previousServer
                    + "' to '" + targetServer + "' — skipping battle cancellation.");
            return;
        }

        BattleRegistry.BattleEntry battle =
                plugin.battleRegistry().getBattleForPlayer(playerUuid).orElse(null);
        if (battle == null) return;

        logger.warning("[ServerSwitch] Player " + event.getPlayer().getUsername()
                + " (" + playerUuid + ") switched from '" + previousServer
                + "' to '" + targetServer
                + "' while in battle " + battle.battleUuid()
                + " — notifying backend to cancel.");

        plugin.backendMessenger().sendPlayerSwitchedServer(
                previousServer, playerUuid, previousServer, targetServer, battle.battleUuid());

        plugin.battleRegistry().removeParticipant(playerUuid);
    }
}
