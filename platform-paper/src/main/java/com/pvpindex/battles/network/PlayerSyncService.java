package com.pvpindex.battles.network;

import com.pvpindex.battles.messaging.NetworkPlayerCache;
import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Publishes player join/leave/switch events directly to Redis and subscribes
 * to the same events from other lobbies to maintain a real-time global player cache.
 */
public final class PlayerSyncService implements Listener {

    private final JavaPlugin plugin;
    private final NetworkRouter router;
    private final String nodeId;
    private NetworkPlayerCache cache;

    public PlayerSyncService(JavaPlugin plugin, NetworkRouter router, String nodeId) {
        this.plugin = plugin;
        this.router = router;
        this.nodeId = nodeId;
    }

    public void setCache(NetworkPlayerCache cache) {
        this.cache = cache;
    }

    /**
     * Adds all currently online players to the cache. Call after
     * {@link #setCache(NetworkPlayerCache)} when the cache is wired
     * after {@link #start()} has already run.
     */
    public void seedLocalPlayers() {
        if (cache == null) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            cache.addPlayer(p.getName(), p.getUniqueId(), nodeId);
        }
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        router.addHandler(NetworkMessageType.PLAYER_JOIN, msg -> {
            String srcNode = msg.payloadString("nodeId");
            if (srcNode == null) srcNode = msg.payloadString("proxyId");
            if (nodeId.equals(srcNode)) return;
            if (cache != null) {
                String name = msg.payloadString("playerName");
                String uuid = msg.payloadString("playerId");
                String server = msg.payloadString("server");
                if (name != null && uuid != null) {
                    cache.addPlayer(name, java.util.UUID.fromString(uuid), server != null ? server : "");
                }
            }
        });

        router.addHandler(NetworkMessageType.PLAYER_LEAVE, msg -> {
            String uuid = msg.payloadString("playerId");
            if (uuid != null && cache != null) {
                cache.removePlayer(java.util.UUID.fromString(uuid));
            }
        });

        router.addHandler(NetworkMessageType.PLAYER_SWITCH_SERVER, msg -> {
            String uuid = msg.payloadString("playerId");
            String server = msg.payloadString("server");
            if (uuid != null && server != null && cache != null) {
                cache.updateServer(java.util.UUID.fromString(uuid), server);
            }
        });

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            publishJoin(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (cache != null) {
            cache.addPlayer(p.getName(), p.getUniqueId(), nodeId);
        }
        publishJoin(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (cache != null) {
            cache.removePlayer(p.getUniqueId());
        }
        router.broadcast(NetworkMessageType.PLAYER_LEAVE, Map.of(
                "playerId", p.getUniqueId().toString(),
                "playerName", p.getName(),
                "nodeId", nodeId
        ));
    }

    private void publishJoin(Player p) {
        if (cache != null) {
            cache.addPlayer(p.getName(), p.getUniqueId(), nodeId);
        }
        router.broadcast(NetworkMessageType.PLAYER_JOIN, Map.of(
                "playerId", p.getUniqueId().toString(),
                "playerName", p.getName(),
                "nodeId", nodeId,
                "server", nodeId
        ));
    }
}
