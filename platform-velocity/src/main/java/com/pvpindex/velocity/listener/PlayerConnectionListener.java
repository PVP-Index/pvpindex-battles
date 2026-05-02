package com.pvpindex.velocity.listener;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.pvpindex.velocity.registry.PlayerServerRegistry;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.UUID;

public final class PlayerConnectionListener {

    private final PvPIndexVelocityPlugin plugin;

    public PlayerConnectionListener(PvPIndexVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        plugin.clearChallengeTransfer(player.getUniqueId());
        player.getCurrentServer().ifPresent(conn -> {
            String serverName = conn.getServer().getServerInfo().getName();
            plugin.playerRegistry().put(player.getUniqueId(), conn.getServer());

            NetworkRouter router = plugin.networkRouter();
            if (router != null) {
                if (event.getPreviousServer() == null) {
                    router.broadcast(NetworkMessageType.PLAYER_JOIN, Map.of(
                            "playerId", player.getUniqueId().toString(),
                            "playerName", player.getUsername(),
                            "proxyId", plugin.config().networkConfig().proxyId(),
                            "server", serverName
                    ));
                } else {
                    router.broadcast(NetworkMessageType.PLAYER_SWITCH_SERVER, Map.of(
                            "playerId", player.getUniqueId().toString(),
                            "playerName", player.getUsername(),
                            "proxyId", plugin.config().networkConfig().proxyId(),
                            "server", serverName
                    ));
                }

                router.playerRegistry().registerPlayer(
                        player.getUniqueId(), player.getUsername(),
                        plugin.config().networkConfig().proxyId(), serverName);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.playerRegistry().remove(uuid);
        plugin.battleRegistry().removeParticipant(uuid);

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            router.broadcast(NetworkMessageType.PLAYER_LEAVE, Map.of(
                    "playerId", uuid.toString(),
                    "proxyId", plugin.config().networkConfig().proxyId()
            ));
            router.playerRegistry().unregisterPlayer(uuid);
        }
    }
}
