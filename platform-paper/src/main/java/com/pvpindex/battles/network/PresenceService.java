package com.pvpindex.battles.network;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real-time player status across all lobbies via Redis pub/sub.
 */
public final class PresenceService implements Listener {

    public enum PlayerStatus { ONLINE, AWAY, IN_BATTLE, IN_QUEUE }

    private final JavaPlugin plugin;
    private final NetworkRouter router;
    private final String nodeId;
    private final ConcurrentHashMap<UUID, PlayerPresence> presenceMap = new ConcurrentHashMap<>();

    public record PlayerPresence(UUID playerId, String playerName, PlayerStatus status,
                                 String nodeId, long timestamp) {}

    public PresenceService(JavaPlugin plugin, NetworkRouter router, String nodeId) {
        this.plugin = plugin;
        this.router = router;
        this.nodeId = nodeId;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        router.addHandler(NetworkMessageType.PRESENCE_UPDATE, msg -> {
            String src = msg.payloadString("nodeId");
            if (nodeId.equals(src)) return;

            String uuid = msg.payloadString("playerId");
            String name = msg.payloadString("playerName");
            String statusStr = msg.payloadString("status");
            if (uuid == null || statusStr == null) return;

            try {
                PlayerStatus status = PlayerStatus.valueOf(statusStr);
                presenceMap.put(UUID.fromString(uuid),
                        new PlayerPresence(UUID.fromString(uuid), name, status, src, System.currentTimeMillis()));
            } catch (IllegalArgumentException ignored) {}
        });

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            updateStatus(p.getUniqueId(), p.getName(), PlayerStatus.ONLINE);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateStatus(event.getPlayer().getUniqueId(), event.getPlayer().getName(), PlayerStatus.ONLINE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        presenceMap.remove(event.getPlayer().getUniqueId());
        router.broadcast(NetworkMessageType.PRESENCE_UPDATE, Map.of(
                "playerId", event.getPlayer().getUniqueId().toString(),
                "playerName", event.getPlayer().getName(),
                "status", "OFFLINE",
                "nodeId", nodeId
        ));
    }

    public void updateStatus(UUID playerId, String playerName, PlayerStatus status) {
        presenceMap.put(playerId, new PlayerPresence(playerId, playerName, status, nodeId, System.currentTimeMillis()));
        router.broadcast(NetworkMessageType.PRESENCE_UPDATE, Map.of(
                "playerId", playerId.toString(),
                "playerName", playerName,
                "status", status.name(),
                "nodeId", nodeId
        ));
    }

    public PlayerPresence getPresence(UUID playerId) {
        return presenceMap.get(playerId);
    }

    public ConcurrentHashMap<UUID, PlayerPresence> allPresence() {
        return presenceMap;
    }
}
