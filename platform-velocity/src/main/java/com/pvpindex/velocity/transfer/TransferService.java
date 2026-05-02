package com.pvpindex.velocity.transfer;

import com.pvpindex.network.NetworkConfig;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handles cross-proxy player transfers for battle challenges.
 * Supports two strategies:
 * <ul>
 *   <li><b>shared_server</b> — Route both players to a shared battle server
 *       accessible from all proxies.</li>
 *   <li><b>transfer_packet</b> — Use MC 1.20.5+ transfer packets to move
 *       the player to the other proxy.</li>
 *   <li><b>both</b> — Prefer shared_server, fall back to transfer_packet.</li>
 * </ul>
 */
public final class TransferService {

    private final PvPIndexVelocityPlugin plugin;
    private final Logger logger;

    public TransferService(PvPIndexVelocityPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Attempt to bring two players from different proxies together for a battle.
     *
     * @param localPlayer    The player on this proxy.
     * @param remoteProxyId  The proxy the other player is on.
     * @param battleServer   Preferred battle server name (if known), or null.
     * @return true if the transfer was initiated successfully.
     */
    public boolean initiateTransfer(Player localPlayer, String remoteProxyId, String battleServer) {
        NetworkConfig netCfg = plugin.config().networkConfig();
        String strategy = netCfg.transferStrategy();

        if ("shared_server".equals(strategy) || "both".equals(strategy)) {
            if (trySharedServer(localPlayer, battleServer, netCfg)) return true;
            if ("shared_server".equals(strategy)) {
                logger.warning("[TransferService] No shared battle server available for player " + localPlayer.getUsername());
                return false;
            }
        }

        if ("transfer_packet".equals(strategy) || "both".equals(strategy)) {
            return tryTransferPacket(localPlayer, remoteProxyId, netCfg);
        }

        logger.warning("[TransferService] Unknown transfer strategy: " + strategy);
        return false;
    }

    private boolean trySharedServer(Player player, String preferredServer, NetworkConfig netCfg) {
        List<String> sharedServers = netCfg.sharedBattleServers();
        if (sharedServers.isEmpty()) return false;

        String targetServerName = preferredServer;
        if (targetServerName == null || !sharedServers.contains(targetServerName)) {
            targetServerName = sharedServers.get(0);
        }

        Optional<RegisteredServer> targetServer = plugin.getServer().getServer(targetServerName);
        if (targetServer.isEmpty()) {
            logger.warning("[TransferService] Shared battle server '" + targetServerName
                    + "' is not registered on this proxy.");
            return false;
        }

        plugin.markChallengeTransfer(player.getUniqueId());
        player.createConnectionRequest(targetServer.get()).fireAndForget();

        logger.info("[TransferService] Transferring " + player.getUsername()
                + " to shared battle server '" + targetServerName + "'");
        return true;
    }

    private boolean tryTransferPacket(Player player, String remoteProxyId, NetworkConfig netCfg) {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) return false;

        plugin.markChallengeTransfer(player.getUniqueId());

        router.sendToProxy(remoteProxyId, NetworkMessageType.TRANSFER_REQUEST, Map.of(
                "playerId", player.getUniqueId().toString(),
                "playerName", player.getUsername(),
                "sourceProxy", netCfg.proxyId()
        ));

        logger.info("[TransferService] Transfer packet request sent for " + player.getUsername()
                + " to proxy " + remoteProxyId);
        return true;
    }
}
