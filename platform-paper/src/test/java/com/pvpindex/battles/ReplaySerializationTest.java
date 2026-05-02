package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.bukkit.plugin.Plugin;

import static org.junit.jupiter.api.Assertions.*;

class ReplaySerializationTest {
    @Test
    void replayContainsRecordedEvents() throws Exception {
        Plugin plugin = Mockito.mock(Plugin.class);
        BattleReplayRecorder recorder = new BattleReplayRecorder(plugin, new ObjectMapper(), ReplayDetailLevel.HIGH);
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.SWORD, "srv", null);
        session.markStarted();
        recorder.start(session);
        recorder.record(session, "player_damage", UUID.randomUUID(), UUID.randomUUID(), Map.of("damage", 4.5));
        session.markFinished();

        Map<String, Object> replay = recorder.buildReplay(session);
        assertEquals(1, replay.get("version"));
        assertTrue(((java.util.List<?>) replay.get("events")).size() >= 1);
    }
}
