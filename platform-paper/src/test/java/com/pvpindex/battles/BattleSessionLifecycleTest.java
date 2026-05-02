package com.pvpindex.battles;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleStatus;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.battle.type.ParticipantResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleSessionLifecycleTest {
    @Test
    void lifecycleChangesToFinished() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.VANILLA, "srv", null);
        session.setStatus(BattleStatus.WAITING);
        session.markStarted();
        session.markFinished();

        assertEquals(BattleStatus.FINISHED, session.getStatus());
        assertNotNull(session.getStartedAt());
        assertNotNull(session.getEndedAt());
    }

    @Test
    void participantResultTracksStats() {
        BattleParticipant p = new BattleParticipant(UUID.randomUUID(), "alice", null);
        p.addKill();
        p.addDeath();
        p.addDamageDealt(5.5);
        p.addDamageTaken(2.0);
        p.addHealingDone(3.0);
        p.setResult(ParticipantResult.WIN);

        assertEquals(1, p.getKills());
        assertEquals(1, p.getDeaths());
        assertEquals(5.5, p.getDamageDealt());
        assertEquals(ParticipantResult.WIN, p.getResult());
    }
}
