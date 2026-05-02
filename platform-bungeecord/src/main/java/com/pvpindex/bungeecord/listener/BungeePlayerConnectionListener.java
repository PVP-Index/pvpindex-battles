package com.pvpindex.bungeecord.listener;

import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Map;
import java.util.UUID;

public final class BungeePlayerConnectionListener implements Listener {

    private final PvPIndexBungeePlugin plugin;

    public BungeePlayerConnectionListener(PvPIndexBungeePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        plugin.clearChallengeTransfer(player.getUniqueId());

        if (player.getServer() == null) return;
        String serverName = player.getServer().getInfo().getName();

        NetworkRouter router = plugin.networkRouter();
        if (router != null) {
            String proxyId = plugin.config().networkConfig().proxyId();

            if (event.getFrom() == null) {
                router.broadcast(NetworkMessageType.PLAYER_JOIN, Map.of(
                        "playerId", player.getUniqueId().toString(),
                        "playerName", player.getName(),
                        "proxyId", proxyId,
                        "server", serverName
                ));
            } else {
                router.broadcast(NetworkMessageType.PLAYER_SWITCH_SERVER, Map.of(
                        "playerId", player.getUniqueId().toString(),
                        "playerName", player.getName(),
                        "proxyId", proxyId,
                        "server", serverName
                ));
            }

            router.playerRegistry().registerPlayer(
                    player.getUniqueId(), player.getName(), proxyId, serverName);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

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
