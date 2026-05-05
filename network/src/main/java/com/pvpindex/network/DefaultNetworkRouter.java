package com.pvpindex.network;

import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.redis.RedisPlayerRegistry;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultNetworkRouter implements NetworkRouter {

    private static final Logger LOGGER = Logger.getLogger(DefaultNetworkRouter.class.getName());

    private final MessageBus messageBus;
    private final PlayerRegistry playerRegistry;
    private final ServerRegistry serverRegistry;
    private final NetworkConfig config;

    private final ConcurrentMap<String, NetworkNode> nodes = new ConcurrentHashMap<>();
    private NetworkNode localNode;

    private ScheduledExecutorService scheduler;

    public DefaultNetworkRouter(MessageBus messageBus, PlayerRegistry playerRegistry,
                                ServerRegistry serverRegistry, NetworkConfig config) {
        this.messageBus = messageBus;
        this.playerRegistry = playerRegistry;
        this.serverRegistry = serverRegistry;
        this.config = config;
    }

    @Override
    public void start() {
        messageBus.subscribe(NetworkMessageType.PROXY_REGISTER, this::handleNodeRegister);
        messageBus.subscribe(NetworkMessageType.PROXY_HEARTBEAT, this::handleNodeHeartbeat);
        messageBus.subscribe(NetworkMessageType.PROXY_SHUTDOWN, this::handleNodeShutdown);
        messageBus.subscribe(NetworkMessageType.PLAYER_JOIN, this::handlePlayerJoin);
        messageBus.subscribe(NetworkMessageType.PLAYER_LEAVE, this::handlePlayerLeave);
        messageBus.subscribe(NetworkMessageType.PLAYER_SWITCH_SERVER, this::handlePlayerSwitchServer);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pvpindex-network-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::heartbeat,
                config.heartbeatIntervalSeconds(),
                config.heartbeatIntervalSeconds(),
                TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(this::pruneTimedOutNodes,
                config.proxyTimeoutSeconds(),
                config.proxyTimeoutSeconds() / 2,
                TimeUnit.SECONDS);

        if (localNode != null) {
            publishNodeRegister();
        }

        LOGGER.info("[PvPIndex Network] Router started for node: " + config.proxyId());
    }

    @Override
    public void shutdown() {
        if (localNode != null) {
            broadcast(NetworkMessageType.PROXY_SHUTDOWN, Map.of(
                    "proxyId", config.proxyId(),
                    "nodeId", config.proxyId()
            ));
        }

        messageBus.unsubscribeAll();

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        LOGGER.info("[PvPIndex Network] Router shut down for node: " + config.proxyId());
    }

    // ── Node-generic methods ────────────────────────────────────────────

    @Override
    public void registerLocalNode(NetworkNode self) {
        this.localNode = self;
        nodes.put(self.nodeId(), self);
        publishNodeRegister();
    }

    @Override
    public Optional<NetworkNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Collection<NetworkNode> allNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public Collection<NetworkNode> onlineNodes() {
        return nodes.values().stream().filter(NetworkNode::isOnline).toList();
    }

    // ── Messaging ───────────────────────────────────────────────────────

    @Override
    public void sendToProxy(String targetProxyId, NetworkMessageType type, Map<String, Object> payload) {
        NetworkMessage msg = NetworkMessage.targeted(config.proxyId(), targetProxyId, type, payload);
        messageBus.publish(msg);
    }

    @Override
    public void broadcast(NetworkMessageType type, Map<String, Object> payload) {
        NetworkMessage msg = NetworkMessage.broadcast(config.proxyId(), type, payload);
        messageBus.publish(msg);
    }

    @Override
    public void sendToPlayer(UUID playerId, NetworkMessageType type, Map<String, Object> payload) {
        playerRegistry.findPlayer(playerId).ifPresent(loc -> {
            Map<String, Object> enriched = new HashMap<>(payload);
            enriched.put("targetPlayer", playerId.toString());
            sendToProxy(loc.proxyId(), type, enriched);
        });
    }

    @Override
    public void addHandler(NetworkMessageType type, Consumer<NetworkMessage> handler) {
        messageBus.subscribe(type, handler);
    }

    @Override
    public PlayerRegistry playerRegistry() { return playerRegistry; }

    @Override
    public ServerRegistry serverRegistry() { return serverRegistry; }

    // ── Internal ────────────────────────────────────────────────────────

    private void publishNodeRegister() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("proxyId", config.proxyId());
        payload.put("nodeId", config.proxyId());
        payload.put("region", config.region());
        payload.put("playerCount", localNode != null ? localNode.playerCount() : 0);
        if (localNode != null) {
            payload.put("nodeType", localNode.nodeType().name());
        }
        broadcast(NetworkMessageType.PROXY_REGISTER, payload);
    }

    private void heartbeat() {
        if (localNode != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("proxyId", config.proxyId());
            payload.put("nodeId", config.proxyId());
            payload.put("region", config.region());
            payload.put("playerCount", localNode.playerCount());
            payload.put("nodeType", localNode.nodeType().name());
            broadcast(NetworkMessageType.PROXY_HEARTBEAT, payload);
        }
    }

    private void pruneTimedOutNodes() {
        nodes.forEach((id, node) -> {
            if (!id.equals(config.proxyId()) && node.isTimedOut(config.proxyTimeoutSeconds())) {
                node.markOffline();
                LOGGER.warning("[PvPIndex Network] Node timed out: " + id);
                if (playerRegistry instanceof RedisPlayerRegistry redisReg) {
                    redisReg.removeAllForProxy(id);
                }
                serverRegistry.unregisterAllForProxy(id);
            }
        });
    }

    private void handleNodeRegister(NetworkMessage msg) {
        String id = msg.payloadString("nodeId");
        if (id == null) id = msg.payloadString("proxyId");
        String region = msg.payloadString("region");
        int players = msg.payloadInt("playerCount", 0);
        if (id == null) return;

        boolean isNew = !nodes.containsKey(id);
        final String nodeId = id;
        NetworkNode node = nodes.computeIfAbsent(nodeId, k -> new NetworkNode(nodeId, region));
        node.heartbeat(players);

        if (isNew) {
            LOGGER.info("[PvPIndex Network] Node registered: " + nodeId + " (region=" + region + ")");
            publishNodeRegister();
        }
    }

    private void handleNodeHeartbeat(NetworkMessage msg) {
        String id = msg.payloadString("nodeId");
        if (id == null) id = msg.payloadString("proxyId");
        int players = msg.payloadInt("playerCount", 0);
        if (id == null) return;

        NetworkNode node = nodes.get(id);
        if (node != null) {
            node.heartbeat(players);
        } else {
            String region = msg.payloadString("region");
            nodes.put(id, new NetworkNode(id, region));
        }
    }

    private void handleNodeShutdown(NetworkMessage msg) {
        String id = msg.payloadString("nodeId");
        if (id == null) id = msg.payloadString("proxyId");
        if (id == null) return;

        NetworkNode node = nodes.get(id);
        if (node != null) {
            node.markOffline();
            LOGGER.info("[PvPIndex Network] Node shut down: " + id);
            if (playerRegistry instanceof RedisPlayerRegistry redisReg) {
                redisReg.removeAllForProxy(id);
            }
            serverRegistry.unregisterAllForProxy(id);
        }
    }

    private void handlePlayerJoin(NetworkMessage msg) {
        String playerIdStr = msg.payloadString("playerId");
        String playerName = msg.payloadString("playerName");
        String proxyId = msg.payloadString("proxyId");
        if (proxyId == null) proxyId = msg.payloadString("nodeId");
        String server = msg.payloadString("server");
        if (playerIdStr == null || playerName == null || proxyId == null) return;

        playerRegistry.registerPlayer(UUID.fromString(playerIdStr), playerName, proxyId, server != null ? server : "");
    }

    private void handlePlayerLeave(NetworkMessage msg) {
        String playerIdStr = msg.payloadString("playerId");
        if (playerIdStr == null) return;
        playerRegistry.unregisterPlayer(UUID.fromString(playerIdStr));
    }

    private void handlePlayerSwitchServer(NetworkMessage msg) {
        String playerIdStr = msg.payloadString("playerId");
        String newServer = msg.payloadString("server");
        if (playerIdStr == null || newServer == null) return;
        playerRegistry.switchServer(UUID.fromString(playerIdStr), newServer);
    }
}
