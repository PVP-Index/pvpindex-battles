package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.api.BattlePayloadFactory;
import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.battle.BattleBatchScheduler;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import com.pvpindex.battles.storage.FileStorageService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class BattleBatchSchedulerTest {

    @Test
    void flushDoesNothingWhenNoActiveBattles() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        BattleService service = createService(true);
        PvPIndexApiClient client = createCountingClient(calls, 200);
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        BattleBatchScheduler scheduler = new BattleBatchScheduler(plugin, service, client, 20, false);
        invokeFlush(scheduler);

        // No active battles → client should not be called.
        assertEquals(0, calls.get());
    }

    @Test
    void flushSendsHeartbeatForActiveBattles() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        BattleService service = createService(true);
        PvPIndexApiClient client = createCountingClient(calls, 200);
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        // Create and start a battle so activeBattles() is non-empty.
        BattleSession s = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null),
                        new BattleParticipant(UUID.randomUUID(), "bob", null)),
                null, Map.of());
        service.startBattle(s.getUuid());

        BattleBatchScheduler scheduler = new BattleBatchScheduler(plugin, service, client, 20, false);
        invokeFlush(scheduler);

        // Give async completion a moment.
        Thread.sleep(50);
        assertEquals(1, calls.get(), "Heartbeat should be sent once for the active battle");
    }

    @Test
    void flushSuppressesAfter404() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        BattleService service = createService(true);
        PvPIndexApiClient client = createCountingClient(calls, 404);
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        BattleSession s = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null),
                        new BattleParticipant(UUID.randomUUID(), "bob", null)),
                null, Map.of());
        service.startBattle(s.getUuid());

        BattleBatchScheduler scheduler = new BattleBatchScheduler(plugin, service, client, 20, true);

        // First flush. hits 404, sets endpointUnavailable.
        invokeFlush(scheduler);
        Thread.sleep(50);
        assertEquals(1, calls.get());

        // Second flush. should be suppressed.
        invokeFlush(scheduler);
        Thread.sleep(50);
        assertEquals(1, calls.get(), "Second flush should be suppressed after 404");
    }

    @Test
    void respectsMaxBatchSize() throws Exception {
        List<List<Map<String, Object>>> captured = new ArrayList<>();
        BattleService service = createService(true);
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        PvPIndexApiClient client = new PvPIndexApiClient(
                createSettings(), new ObjectMapper()) {
            @Override
            public CompletableFuture<PostResult> sendHeartbeat(List<Map<String, Object>> battles) {
                captured.add(new ArrayList<>(battles));
                return CompletableFuture.completedFuture(PostResult.success(200));
            }
        };

        // Create 5 active battles.
        for (int i = 0; i < 5; i++) {
            BattleSession s = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                    List.of(new BattleParticipant(UUID.randomUUID(), "p" + i + "a", null),
                            new BattleParticipant(UUID.randomUUID(), "p" + i + "b", null)),
                    null, Map.of());
            service.startBattle(s.getUuid());
        }

        BattleBatchScheduler scheduler = new BattleBatchScheduler(plugin, service, client, 3, false);
        invokeFlush(scheduler);
        Thread.sleep(50);

        assertFalse(captured.isEmpty(), "Should have captured at least one flush");
        assertTrue(captured.get(0).size() <= 3, "Batch should respect maxBatchSize=3");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void invokeFlush(BattleBatchScheduler scheduler) throws Exception {
        var m = BattleBatchScheduler.class.getDeclaredMethod("flush");
        m.setAccessible(true);
        m.invoke(scheduler);
    }

    private PvPIndexApiClient createCountingClient(AtomicInteger calls, int statusCode) {
        return new PvPIndexApiClient(createSettings(), new ObjectMapper()) {
            @Override
            public CompletableFuture<PostResult> sendHeartbeat(List<Map<String, Object>> battles) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(
                        statusCode >= 200 && statusCode < 300
                                ? PostResult.success(statusCode)
                                : PostResult.httpError(statusCode, ""));
            }
        };
    }

    private PluginSettings createSettings() {
        return new PluginSettings(
                "http://localhost", "test-api-key", 1, 1, 1, 2.0, 5, 0, false, "srv",
                Set.of(GameModeType.values()), Set.of(BattleType.values()), ReplayDetailLevel.HIGH,
                true, false, 0, 0, true, false,
                false, 0.1, 2,
                false, 40, 20,
                100,
                false, "", 0,
                false,
                true, java.util.List.of("battle")
        );
    }

    private BattleService createService(boolean apiSuccess) throws Exception {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        PluginSettings settings = createSettings();
        BattleReplayRecorder recorder = new BattleReplayRecorder(plugin, new ObjectMapper(), ReplayDetailLevel.HIGH);
        PvPIndexApiClient apiClient = new PvPIndexApiClient(settings, new ObjectMapper()) {
            @Override
            public CompletableFuture<PostResult> submitBattle(Map<String, Object> payload) {
                return CompletableFuture.completedFuture(
                        apiSuccess ? PostResult.success(200) : PostResult.exception("test"));
            }
        };
        Path temp = Files.createTempDirectory("pvp-batch");
        FileStorageService storage = new FileStorageService(temp, new ObjectMapper());
        storage.initialise();
        return new BattleService(plugin, settings, recorder, apiClient, new BattlePayloadFactory(), storage);
    }
}
