package com.pvpindex.battles.network;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.NodeType;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Smart region selection for battles based on load and proximity.
 */
public final class RoutingService {

    public enum RoutingStrategy { NEAREST_TO_CHALLENGER, LOWEST_LATENCY, LEAST_LOADED, SHARED_SERVER }

    private final NetworkRouter router;
    private final String nodeId;
    private final ConcurrentHashMap<String, ServerLoad> serverLoads = new ConcurrentHashMap<>();

    public record ServerLoad(String serverId, String nodeId, String region,
                             int activeBattles, int playerCount, long timestamp) {}

    public RoutingService(NetworkRouter router, String nodeId) {
        this.router = router;
        this.nodeId = nodeId;
    }

    public void reportLoad(String serverId, int activeBattles, int playerCount) {
        router.broadcast(NetworkMessageType.ROUTE_RESPONSE, Map.of(
                "serverId", serverId,
                "nodeId", nodeId,
                "activeBattles", String.valueOf(activeBattles),
                "playerCount", String.valueOf(playerCount)
        ));
    }

    public Optional<String> findBestServer(String challengerRegion, String targetRegion,
                                           RoutingStrategy strategy) {
        return serverLoads.values().stream()
                .filter(s -> System.currentTimeMillis() - s.timestamp() < 60_000)
                .min(Comparator.comparingInt(ServerLoad::activeBattles))
                .map(ServerLoad::serverId);
    }

    public void start() {
        router.addHandler(NetworkMessageType.ROUTE_RESPONSE, msg -> {
            String serverId = msg.payloadString("serverId");
            String srcNode = msg.payloadString("nodeId");
            if (serverId == null) return;

            NetworkNode node = router.getNode(srcNode).orElse(null);
            String region = node != null ? node.region() : "unknown";

            serverLoads.put(serverId, new ServerLoad(
                    serverId, srcNode, region,
                    msg.payloadInt("activeBattles", 0),
                    msg.payloadInt("playerCount", 0),
                    System.currentTimeMillis()
            ));
        });
    }
}
