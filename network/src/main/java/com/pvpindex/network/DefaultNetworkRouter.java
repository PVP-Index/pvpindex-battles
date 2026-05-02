package com.pvpindex.network;

import com.pvpindex.network.node.ProxyNode;
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

    private final ConcurrentMap<String, ProxyNode> proxies = new ConcurrentHashMap<>();
    private ProxyNode localProxy;

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
        messageBus.subscribe(NetworkMessageType.PROXY_REGISTER, this::handleProxyRegister);
        messageBus.subscribe(NetworkMessageType.PROXY_HEARTBEAT, this::handleProxyHeartbeat);
        messageBus.subscribe(NetworkMessageType.PROXY_SHUTDOWN, this::handleProxyShutdown);
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

        scheduler.scheduleAtFixedRate(this::pruneTimedOutProxies,
                config.proxyTimeoutSeconds(),
                config.proxyTimeoutSeconds() / 2,
                TimeUnit.SECONDS);

        if (localProxy != null) {
            publishProxyRegister();
        }

        LOGGER.info("[PvPIndex Network] Router started for proxy: " + config.proxyId());
    }

    @Override
    public void shutdown() {
        if (localProxy != null) {
            broadcast(NetworkMessageType.PROXY_SHUTDOWN, Map.of(
                    "proxyId", config.proxyId()
            ));
        }

        messageBus.unsubscribeAll();

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        LOGGER.info("[PvPIndex Network] Router shut down for proxy: " + config.proxyId());
    }

    @Override
    public void registerLocalProxy(ProxyNode self) {
        this.localProxy = self;
        proxies.put(self.proxyId(), self);
        publishProxyRegister();
    }

    @Override
    public Optional<ProxyNode> getProxy(String proxyId) {
        return Optional.ofNullable(proxies.get(proxyId));
    }

    @Override
    public Collection<ProxyNode> allProxies() {
        return Collections.unmodifiableCollection(proxies.values());
    }

    @Override
    public Collection<ProxyNode> onlineProxies() {
        return proxies.values().stream().filter(ProxyNode::isOnline).toList();
    }

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

    private void publishProxyRegister() {
        broadcast(NetworkMessageType.PROXY_REGISTER, Map.of(
                "proxyId", config.proxyId(),
                "region", config.region(),
                "playerCount", localProxy != null ? localProxy.playerCount() : 0
        ));
    }

    private void heartbeat() {
        if (localProxy != null) {
            broadcast(NetworkMessageType.PROXY_HEARTBEAT, Map.of(
                    "proxyId", config.proxyId(),
                    "region", config.region(),
                    "playerCount", localProxy.playerCount()
            ));
        }
    }

    private void pruneTimedOutProxies() {
        proxies.forEach((id, node) -> {
            if (!id.equals(config.proxyId()) && node.isTimedOut(config.proxyTimeoutSeconds())) {
                node.markOffline();
                LOGGER.warning("[PvPIndex Network] Proxy timed out: " + id);
                if (playerRegistry instanceof RedisPlayerRegistry redisReg) {
                    redisReg.removeAllForProxy(id);
                }
                serverRegistry.unregisterAllForProxy(id);
            }
        });
    }

    private void handleProxyRegister(NetworkMessage msg) {
        String id = msg.payloadString("proxyId");
        String region = msg.payloadString("region");
        int players = msg.payloadInt("playerCount", 0);
        if (id == null) return;

        ProxyNode node = proxies.computeIfAbsent(id, k -> new ProxyNode(id, region));
        node.heartbeat(players);
        LOGGER.info("[PvPIndex Network] Proxy registered: " + id + " (region=" + region + ")");

        publishProxyRegister();
    }

    private void handleProxyHeartbeat(NetworkMessage msg) {
        String id = msg.payloadString("proxyId");
        int players = msg.payloadInt("playerCount", 0);
        if (id == null) return;

        ProxyNode node = proxies.get(id);
        if (node != null) {
            node.heartbeat(players);
        } else {
            String region = msg.payloadString("region");
            proxies.put(id, new ProxyNode(id, region));
        }
    }

    private void handleProxyShutdown(NetworkMessage msg) {
        String id = msg.payloadString("proxyId");
        if (id == null) return;

        ProxyNode node = proxies.get(id);
        if (node != null) {
            node.markOffline();
            LOGGER.info("[PvPIndex Network] Proxy shut down: " + id);
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
