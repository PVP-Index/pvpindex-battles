package com.pvpindex.battles;

import com.pvpindex.battles.api.BattlePayloadFactory;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattlePayloadFactoryTest {
    @Test
    void generatesExpectedPayload() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.RANKED_ARENA, GameModeType.UHC, "srv", "arena1");
        session.addParticipant(new BattleParticipant(UUID.randomUUID(), "alice", "A"));
        session.addParticipant(new BattleParticipant(UUID.randomUUID(), "bob", "B"));

        Map<String, Object> payload = new BattlePayloadFactory().toPayload(session, Map.of("events", java.util.List.of()), null);
        assertEquals("RANKED_ARENA", payload.get("battle_type"));
        assertEquals("uhc", payload.get("game_mode_slug"));
        assertEquals(2, ((java.util.List<?>) payload.get("participants")).size());
    }
}
