package com.pvpindex.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkConfigValidationTest {

    @Test
    void validConfigPasses() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("us-1")
                .region("us-east")
                .enabled(true)
                .redisHost("localhost")
                .redisPort(6379)
                .redisPoolSize(4)
                .transferStrategy("both")
                .build();

        assertTrue(cfg.validate().isEmpty());
    }

    @Test
    void blankProxyIdFails() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("  ")
                .enabled(false)
                .build();

        List<String> errors = cfg.validate();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("proxy_id"));
    }

    @Test
    void enabledWithBlankHostFails() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("p1")
                .enabled(true)
                .redisHost("  ")
                .build();

        List<String> errors = cfg.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("redis.host")));
    }

    @Test
    void invalidPortFails() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("p1")
                .enabled(true)
                .redisHost("localhost")
                .redisPort(99999)
                .build();

        List<String> errors = cfg.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("redis.port")));
    }

    @Test
    void invalidTransferStrategyFails() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("p1")
                .enabled(false)
                .transferStrategy("invalid")
                .build();

        List<String> errors = cfg.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("transfer_strategy")));
    }

    @Test
    void disabledNetworkSkipsRedisValidation() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("p1")
                .enabled(false)
                .build();

        assertTrue(cfg.validate().isEmpty());
    }

    @Test
    void defaultsAreApplied() {
        NetworkConfig cfg = NetworkConfig.builder()
                .proxyId("p1")
                .build();

        assertEquals("default", cfg.region());
        assertEquals("localhost", cfg.redisHost());
        assertEquals(6379, cfg.redisPort());
        assertEquals(8, cfg.redisPoolSize());
        assertEquals(5, cfg.reconnectIntervalSeconds());
        assertEquals(10, cfg.messageTimeoutSeconds());
        assertEquals(15, cfg.heartbeatIntervalSeconds());
        assertEquals(45, cfg.proxyTimeoutSeconds());
        assertEquals("shared_server", cfg.transferStrategy());
        assertTrue(cfg.sharedBattleServers().isEmpty());
        assertFalse(cfg.debugLogging());
    }
}
