package com.pvpindex.network.redis;

import com.pvpindex.network.NetworkConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(RedisConnectionManager.class.getName());

    private final NetworkConfig config;
    private volatile JedisPool pool;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RedisConnectionManager(NetworkConfig config) {
        this.config = config;
    }

    public void connect() {
        if (pool != null) return;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.redisPoolSize());
        poolConfig.setMaxIdle(config.redisPoolSize());
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        String password = config.redisPassword();
        boolean hasPassword = password != null && !password.isBlank();

        pool = new JedisPool(
                poolConfig,
                config.redisHost(),
                config.redisPort(),
                2000,
                hasPassword ? password : null,
                config.redisDatabase()
        );

        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }

        LOGGER.info("[PvPIndex Network] Connected to Redis at " + config.redisHost() + ":" + config.redisPort());
    }

    public Jedis getResource() {
        JedisPool p = pool;
        if (p == null || p.isClosed()) {
            throw new IllegalStateException("Redis connection pool is not available");
        }
        return p.getResource();
    }

    public JedisPool getPool() {
        return pool;
    }

    public boolean isConnected() {
        JedisPool p = pool;
        if (p == null || p.isClosed()) return false;
        try (Jedis jedis = p.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void disconnect() {
        if (closed.compareAndSet(false, true)) {
            JedisPool p = pool;
            if (p != null && !p.isClosed()) {
                p.close();
                LOGGER.info("[PvPIndex Network] Redis connection pool closed.");
            }
            pool = null;
        }
    }

    public void reconnect() {
        LOGGER.info("[PvPIndex Network] Attempting Redis reconnection...");
        JedisPool old = pool;
        pool = null;
        if (old != null && !old.isClosed()) {
            try { old.close(); } catch (Exception ignored) {}
        }
        closed.set(false);
        try {
            connect();
            LOGGER.info("[PvPIndex Network] Redis reconnected successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[PvPIndex Network] Redis reconnection failed: " + e.getMessage());
        }
    }
}
