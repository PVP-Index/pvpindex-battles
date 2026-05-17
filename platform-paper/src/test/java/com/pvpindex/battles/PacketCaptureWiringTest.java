package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.api.BattlePayloadFactory;
import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.replay.PacketCaptureService;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import com.pvpindex.battles.replay.ReplayFrame;
import com.pvpindex.battles.replay.ReplaySettings;
import com.pvpindex.battles.storage.FileStorageService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link BattleService} correctly calls
 * {@link PacketCaptureService#beginRecording} on start and
 * {@link PacketCaptureService#finishRecording} on submit.
 */
class PacketCaptureWiringTest {

    @Test
    void beginRecordingIsCalledOnStartBattle() throws Exception {
        Plugin plugin = mockPlugin();
        PacketCaptureService capture = createCapture(plugin);
        BattleService service = createService(plugin, capture);

        BattleSession session = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null),
                        new BattleParticipant(UUID.randomUUID(), "bob", null)),
                null, Map.of());

        // Before start — no frame bucket for this session.
        assertNull(rawFrames(capture).get(session.getUuid()),
                "Frame bucket should not exist before startBattle");

        service.startBattle(session.getUuid());

        assertNotNull(rawFrames(capture).get(session.getUuid()),
                "Frame bucket should exist after startBattle");
    }

    @Test
    void finishRecordingIsCalledOnSubmitBattle() throws Exception {
        Plugin plugin = mockPlugin();
        PacketCaptureService capture = createCapture(plugin);
        BattleService service = createService(plugin, capture);

        BattleSession session = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null),
                        new BattleParticipant(UUID.randomUUID(), "bob", null)),
                null, Map.of());
        service.startBattle(session.getUuid());

        // Manually complete the session so submitBattle doesn't reject it.
        session.markFinished();
        session.getWinners().add(session.getParticipants().get(0).getUuid());

        service.submitBattle(session.getUuid());
        Thread.sleep(100);

        // After submit, finishRecording should have removed the bucket.
        assertNull(rawFrames(capture).get(session.getUuid()),
                "Frame bucket should be removed after submitBattle");
    }

    @Test
    void framesIncludedInSubmitPayload() throws Exception {
        Plugin plugin = mockPlugin();
        PacketCaptureService capture = createCapture(plugin);

        // Pre-inject a synthetic frame so we can verify it ends up in the payload.
        List<Map<String, Object>> capturedPayloads = new java.util.ArrayList<>();
        PvPIndexApiClient apiClient = new PvPIndexApiClient(createSettings(), new ObjectMapper()) {
            @Override
            public CompletableFuture<PostResult> submitBattle(Map<String, Object> payload) {
                capturedPayloads.add(payload);
                return CompletableFuture.completedFuture(PostResult.success(200));
            }
        };
        BattleService service = createService(plugin, capture, apiClient);

        BattleSession session = service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null),
                        new BattleParticipant(UUID.randomUUID(), "bob", null)),
                null, Map.of());
        service.startBattle(session.getUuid());

        // Inject a synthetic frame directly into the capture service bucket.
        List<ReplayFrame> bucket = rawFrames(capture).get(session.getUuid());
        assertNotNull(bucket);
        bucket.add(ReplayFrame.minimal(1L, session.getParticipants().get(0).getUuid(), 0, 64, 0, 0, 0));

        // Finish and submit.
        session.markFinished();
        session.getWinners().add(session.getParticipants().get(0).getUuid());
        service.submitBattle(session.getUuid());
        Thread.sleep(100);

        assertFalse(capturedPayloads.isEmpty(), "At least one payload should have been submitted");
        Map<?, ?> replayData = (Map<?, ?>) capturedPayloads.get(0).get("replay_data");
        assertNotNull(replayData, "Payload should contain replay_data");
        assertNotNull(replayData.get("frames"), "replay_data should contain frames when capture is wired");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, List<ReplayFrame>> rawFrames(PacketCaptureService svc) throws Exception {
        Field f = PacketCaptureService.class.getDeclaredField("frames");
        f.setAccessible(true);
        return (ConcurrentHashMap<UUID, List<ReplayFrame>>) f.get(svc);
    }

    private Plugin mockPlugin() {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        return plugin;
    }

    private PacketCaptureService createCapture(Plugin plugin) {
        return new PacketCaptureService(plugin, ReplaySettings.defaults());
    }

    private BattleService createService(Plugin plugin, PacketCaptureService capture) throws Exception {
        return createService(plugin, capture, null);
    }

    private BattleService createService(Plugin plugin, PacketCaptureService capture,
            PvPIndexApiClient overrideClient) throws Exception {
        PluginSettings settings = createSettings();
        BattleReplayRecorder recorder = new BattleReplayRecorder(plugin, new ObjectMapper(), ReplayDetailLevel.HIGH);
        PvPIndexApiClient apiClient = overrideClient != null ? overrideClient
                : new PvPIndexApiClient(settings, new ObjectMapper()) {
                    @Override
                    public CompletableFuture<PostResult> submitBattle(Map<String, Object> payload) {
                        return CompletableFuture.completedFuture(PostResult.success(200));
                    }
                };
        Path temp = Files.createTempDirectory("pvp-wiring");
        FileStorageService storage = new FileStorageService(temp, new ObjectMapper());
        storage.initialize();
        BattleService service = new BattleService(plugin, settings, recorder, apiClient,
                new BattlePayloadFactory(), storage);
        service.setPacketCaptureService(capture);
        return service;
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
                false
        );
    }
}
