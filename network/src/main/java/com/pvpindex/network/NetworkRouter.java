package com.pvpindex.network;

import com.pvpindex.network.node.ProxyNode;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface NetworkRouter {

    void start();

    void shutdown();

    void registerLocalProxy(ProxyNode self);

    Optional<ProxyNode> getProxy(String proxyId);

    Collection<ProxyNode> allProxies();

    Collection<ProxyNode> onlineProxies();

    void sendToProxy(String targetProxyId, NetworkMessageType type, Map<String, Object> payload);

    void broadcast(NetworkMessageType type, Map<String, Object> payload);

    void sendToPlayer(UUID playerId, NetworkMessageType type, Map<String, Object> payload);

    void addHandler(NetworkMessageType type, Consumer<NetworkMessage> handler);

    PlayerRegistry playerRegistry();

    ServerRegistry serverRegistry();
}
