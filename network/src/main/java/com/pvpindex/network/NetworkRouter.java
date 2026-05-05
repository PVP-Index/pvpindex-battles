package com.pvpindex.network;

import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.ProxyNode;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface NetworkRouter {

    void start();

    void shutdown();

    // ── Node-generic methods ────────────────────────────────────────────

    void registerLocalNode(NetworkNode self);

    Optional<NetworkNode> getNode(String nodeId);

    Collection<NetworkNode> allNodes();

    Collection<NetworkNode> onlineNodes();

    // ── Backward-compatible proxy methods (delegate to node methods) ────

    /** @deprecated Use {@link #registerLocalNode(NetworkNode)} */
    @Deprecated
    default void registerLocalProxy(ProxyNode self) {
        registerLocalNode(self);
    }

    /** @deprecated Use {@link #getNode(String)} */
    @Deprecated
    default Optional<ProxyNode> getProxy(String proxyId) {
        return getNode(proxyId)
                .filter(n -> n instanceof ProxyNode)
                .map(n -> (ProxyNode) n);
    }

    /** @deprecated Use {@link #allNodes()} */
    @Deprecated
    default Collection<ProxyNode> allProxies() {
        return allNodes().stream()
                .filter(n -> n instanceof ProxyNode)
                .map(n -> (ProxyNode) n)
                .toList();
    }

    /** @deprecated Use {@link #onlineNodes()} */
    @Deprecated
    default Collection<ProxyNode> onlineProxies() {
        return onlineNodes().stream()
                .filter(n -> n instanceof ProxyNode)
                .map(n -> (ProxyNode) n)
                .toList();
    }

    // ── Messaging ───────────────────────────────────────────────────────

    void sendToProxy(String targetProxyId, NetworkMessageType type, Map<String, Object> payload);

    /** Send a message to a specific node by ID. Alias for sendToProxy. */
    default void sendToNode(String targetNodeId, NetworkMessageType type, Map<String, Object> payload) {
        sendToProxy(targetNodeId, type, payload);
    }

    void broadcast(NetworkMessageType type, Map<String, Object> payload);

    void sendToPlayer(UUID playerId, NetworkMessageType type, Map<String, Object> payload);

    void addHandler(NetworkMessageType type, Consumer<NetworkMessage> handler);

    PlayerRegistry playerRegistry();

    ServerRegistry serverRegistry();
}
