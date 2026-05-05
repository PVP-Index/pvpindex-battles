package com.pvpindex.network;

import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.ProxyNode;
import com.pvpindex.network.redis.RedisPlayerRegistry;
import com.pvpindex.network.redis.RedisServerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CrossProxyRouterTest {

    private FakeMessageBus bus;
    private RedisPlayerRegistry playerReg;
    private RedisServerRegistry serverReg;
    private DefaultNetworkRouter router;
    private NetworkConfig config;

    @BeforeEach
    void setup() {
        config = NetworkConfig.builder()
                .proxyId("us-1")
                .region("us-east")
                .enabled(true)
                .redisHost("localhost")
                .build();

        bus = new FakeMessageBus();
        playerReg = new RedisPlayerRegistry();
        serverReg = new RedisServerRegistry();
        router = new DefaultNetworkRouter(bus, playerReg, serverReg, config);
    }

    @Test
    void registerLocalProxyPublishesBroadcast() {
        router.start();
        router.registerLocalProxy(new ProxyNode("us-1", "us-east"));

        assertFalse(bus.published.isEmpty());
        NetworkMessage last = bus.published.get(bus.published.size() - 1);
        assertEquals(NetworkMessageType.PROXY_REGISTER, last.type());
        assertEquals("us-1", last.sourceProxyId());
        assertTrue(last.isBroadcast());
    }

    @Test
    void sendToProxyCreatesTargetedMessage() {
        router.start();
        router.sendToProxy("eu-1", NetworkMessageType.CHALLENGE_SEND, Map.of("test", "val"));

        NetworkMessage msg = bus.published.get(bus.published.size() - 1);
        assertEquals("eu-1", msg.targetProxyId());
        assertFalse(msg.isBroadcast());
    }

    @Test
    void broadcastCreatesUntargetedMessage() {
        router.start();
        router.broadcast(NetworkMessageType.GLOBAL_BROADCAST, Map.of("text", "hello"));

        NetworkMessage msg = bus.published.get(bus.published.size() - 1);
        assertNull(msg.targetProxyId());
        assertTrue(msg.isBroadcast());
    }

    @Test
    void sendToPlayerRoutesToCorrectProxy() {
        router.start();
        UUID playerId = UUID.randomUUID();
        playerReg.registerPlayer(playerId, "Steve", "eu-1", "lobby");

        router.sendToPlayer(playerId, NetworkMessageType.CHALLENGE_SEND, Map.of("challenger", "Alex"));

        NetworkMessage msg = bus.published.get(bus.published.size() - 1);
        assertEquals("eu-1", msg.targetProxyId());
        assertEquals(playerId.toString(), msg.payloadString("targetPlayer"));
    }

    @Test
    void playerRegistryTracksPlayers() {
        playerReg.registerPlayer(UUID.randomUUID(), "Alice", "us-1", "survival");
        playerReg.registerPlayer(UUID.randomUUID(), "Bob", "eu-1", "lobby");

        assertEquals(2, playerReg.globalPlayerCount());
        assertEquals(1, playerReg.proxyPlayerCount("us-1"));
        assertTrue(playerReg.findPlayerByName("alice").isPresent());
    }

    @Test
    void playerServerSwitchUpdatesLocation() {
        UUID id = UUID.randomUUID();
        playerReg.registerPlayer(id, "Alice", "us-1", "lobby");
        playerReg.switchServer(id, "arena");

        assertEquals("arena", playerReg.findPlayer(id).orElseThrow().serverName());
    }

    @Test
    void serverRegistryTracksServers() {
        serverReg.registerServer("us-1", "lobby", "localhost:25565");
        serverReg.registerServer("us-1", "arena", "localhost:25566");
        serverReg.registerServer("eu-1", "lobby", "localhost:25567");

        assertEquals(3, serverReg.globalServerCount());
        assertEquals(2, serverReg.serversOnProxy("us-1").size());
        assertEquals("us-1", serverReg.findProxyForServer("arena").orElseThrow());
    }

    @Test
    void removeAllForProxyCleansUp() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        playerReg.registerPlayer(p1, "A", "us-1", "lobby");
        playerReg.registerPlayer(p2, "B", "us-1", "arena");
        serverReg.registerServer("us-1", "lobby", "addr");

        playerReg.removeAllForProxy("us-1");
        serverReg.unregisterAllForProxy("us-1");

        assertEquals(0, playerReg.proxyPlayerCount("us-1"));
        assertTrue(serverReg.serversOnProxy("us-1").isEmpty());
    }

    @Test
    void proxyNodeTimeoutDetection() {
        ProxyNode node = new ProxyNode("old", "region");
        assertFalse(node.isTimedOut(60));
        assertTrue(node.isTimedOut(0));
    }

    static class FakeMessageBus implements MessageBus {
        final List<NetworkMessage> published = new CopyOnWriteArrayList<>();
        final Map<NetworkMessageType, List<Consumer<NetworkMessage>>> handlers = new HashMap<>();

        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean isConnected() { return true; }

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public void subscribe(NetworkMessageType type, Consumer<NetworkMessage> handler) {
            handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        }

        @Override public void unsubscribeAll() { handlers.clear(); }

        void deliver(NetworkMessage msg) {
            List<Consumer<NetworkMessage>> list = handlers.get(msg.type());
            if (list != null) list.forEach(h -> h.accept(msg));
        }
    }
}
