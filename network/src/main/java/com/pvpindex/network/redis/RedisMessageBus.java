package com.pvpindex.network.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.network.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisMessageBus implements MessageBus {

    private static final Logger LOGGER = Logger.getLogger(RedisMessageBus.class.getName());
    private static final String CHANNEL = "pvpindex:network";

    private final RedisConnectionManager connectionManager;
    private final NetworkConfig config;
    private final ObjectMapper mapper;
    private final MessageDeduplicator deduplicator;
    private final ConcurrentMap<NetworkMessageType, List<Consumer<NetworkMessage>>> handlers = new ConcurrentHashMap<>();

    private volatile JedisPubSub subscriber;
    private volatile ExecutorService subscribeExecutor;
    private volatile boolean running;

    public RedisMessageBus(RedisConnectionManager connectionManager, NetworkConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.mapper = new ObjectMapper();
        this.deduplicator = new MessageDeduplicator(10_000,
                TimeUnit.SECONDS.toMillis(config.messageTimeoutSeconds() * 3L));
    }

    @Override
    public void connect() throws Exception {
        connectionManager.connect();
        running = true;
        subscribeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pvpindex-redis-sub");
            t.setDaemon(true);
            return t;
        });
        subscribeExecutor.submit(this::subscribeLoop);
    }

    @Override
    public void disconnect() {
        running = false;
        if (subscriber != null) {
            try { subscriber.unsubscribe(); } catch (Exception ignored) {}
        }
        if (subscribeExecutor != null) {
            subscribeExecutor.shutdownNow();
        }
        connectionManager.disconnect();
    }

    @Override
    public boolean isConnected() {
        return running && connectionManager.isConnected();
    }

    @Override
    public void publish(NetworkMessage message) {
        try {
            String json = mapper.writeValueAsString(message);
            try (Jedis jedis = connectionManager.getResource()) {
                jedis.publish(CHANNEL, json);
            }
            if (config.debugLogging()) {
                LOGGER.info("[PvPIndex Network] Published: " + message);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[PvPIndex Network] Failed to publish message: " + e.getMessage());
        }
    }

    @Override
    public void subscribe(NetworkMessageType type, Consumer<NetworkMessage> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void unsubscribeAll() {
        handlers.clear();
    }

    private void subscribeLoop() {
        while (running) {
            try (Jedis jedis = connectionManager.getResource()) {
                subscriber = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String rawJson) {
                        handleIncoming(rawJson);
                    }
                };
                jedis.subscribe(subscriber, CHANNEL);
            } catch (Exception e) {
                if (!running) break;
                LOGGER.log(Level.WARNING, "[PvPIndex Network] Redis subscription lost, reconnecting in "
                        + config.reconnectIntervalSeconds() + "s: " + e.getMessage());
                try {
                    Thread.sleep(config.reconnectIntervalSeconds() * 1000L);
                    connectionManager.reconnect();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleIncoming(String rawJson) {
        try {
            NetworkMessage msg = mapper.readValue(rawJson, NetworkMessage.class);

            if (msg.sourceProxyId().equals(config.proxyId())) return;
            if (!msg.isBroadcast() && !msg.targetProxyId().equals(config.proxyId())) return;
            if (deduplicator.isDuplicate(msg.messageId())) return;

            if (config.debugLogging()) {
                LOGGER.info("[PvPIndex Network] Received: " + msg);
            }

            List<Consumer<NetworkMessage>> list = handlers.get(msg.type());
            if (list != null) {
                for (Consumer<NetworkMessage> handler : list) {
                    try {
                        handler.accept(msg);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "[PvPIndex Network] Handler error for " + msg.type() + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[PvPIndex Network] Failed to deserialize message: " + e.getMessage());
        }
    }
}
