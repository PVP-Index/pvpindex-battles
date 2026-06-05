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
import com.pvpindex.battles.replay.ReplayDetailLevel;
import com.pvpindex.battles.storage.FileStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class BattleServiceTest {

    @Test
    void practiceBattlesRemainEloNeutralAndPreventDuplicates() throws Exception {
        BattleService service = createService(true);
        UUID player = UUID.randomUUID();
        BattleSession session = service.createBattle(BattleType.PRACTICE_BATTLE, GameModeType.VANILLA,
                List.of(new BattleParticipant(player, "alice", null), new BattleParticipant(UUID.randomUUID(), "bob", null)), null, Map.of());
        assertThrows(IllegalStateException.class, () -> service.createBattle(BattleType.DUEL, GameModeType.VANILLA,
                List.of(new BattleParticipant(player, "alice", null), new BattleParticipant(UUID.randomUUID(), "eve", null)), null, Map.of()));

        service.startBattle(session.getUuid());
        session.markStarted();
        session.markFinished();
        session.getParticipants().forEach(p -> p.setElo(1200, 1210));
        service.finishBattle(session.getUuid(), List.of(session.getParticipants().getFirst().getUuid()));

        assertNull(session.getParticipants().getFirst().getEloBefore());
        assertNull(session.getParticipants().getFirst().getEloAfter());
    }

    @Test
    void rankedBattleSubmissionMarksSubmitted() throws Exception {
        BattleService service = createService(true);
        BattleSession session = service.createBattle(BattleType.RANKED_ARENA, GameModeType.UHC,
                List.of(new BattleParticipant(UUID.randomUUID(), "alice", null), new BattleParticipant(UUID.randomUUID(), "bob", null)), null, Map.of());
        service.startBattle(session.getUuid());
        session.markFinished();
        session.getStartedAt();
        session.getMetadata().put("tournament_id", "none");
        session.getWinners().add(session.getParticipants().getFirst().getUuid());

        service.submitBattle(session.getUuid());
        Thread.sleep(100);
        assertEquals(com.pvpindex.battles.battle.type.BattleStatus.SUBMITTED, session.getStatus());
    }

    private BattleService createService(boolean apiSuccess) throws Exception {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        PluginSettings settings = new PluginSettings(
                "http://localhost", "test-api-key", 1, 1, 1, 2.0, 5, 0, false, "srv",
                Set.of(GameModeType.values()), Set.of(BattleType.values()), ReplayDetailLevel.HIGH,
                true, false, 0, 0, true, false,
                // velocity tracking
                false, 0.1, 2,
                // battle_batch
                false, 40, 20,
                // cleanup
                100,
                // proxy
                false, "", 0,
                // teams guard
                false,
                // command blocking
                true, java.util.List.of("battle")
        );
        BattleReplayRecorder recorder = new BattleReplayRecorder(plugin, new ObjectMapper(), ReplayDetailLevel.HIGH);
        PvPIndexApiClient apiClient = new PvPIndexApiClient(settings, new ObjectMapper()) {
            @Override
            public CompletableFuture<PvPIndexApiClient.PostResult> submitBattle(Map<String, Object> payload) {
                return CompletableFuture.completedFuture(
                        apiSuccess ? PvPIndexApiClient.PostResult.success(200) : PvPIndexApiClient.PostResult.exception("test"));
            }
        };
        Path temp = Files.createTempDirectory("pvp-service");
        FileStorageService storage = new FileStorageService(temp, new ObjectMapper());
        storage.initialise();
        return new BattleService(plugin, settings, recorder, apiClient, new BattlePayloadFactory(), storage);
    }
}
