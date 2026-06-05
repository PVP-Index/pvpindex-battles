package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import com.pvpindex.battles.replay.ReplayEvent;
import com.pvpindex.battles.velocity.VelocityTracker;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class VelocityTrackerTest {

    private BattleReplayRecorder recorder;
    private BattleSession session;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        recorder = new BattleReplayRecorder(plugin, new ObjectMapper(), ReplayDetailLevel.HIGH);
        playerUuid = UUID.randomUUID();
        session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.VANILLA, "srv", "arena1");
        session.addParticipant(new BattleParticipant(playerUuid, "alice", null));
        recorder.start(session);
    }

    @Test
    void noEventOnFirstTick() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.1, 1);
        // First tick just stores the baseline. no previous value, so no event.
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0);
        assertTrue(eventsFor(session).stream().noneMatch(e -> "velocity_change".equals(e.type())));
    }

    @Test
    void noEventBelowThreshold() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.5, 1);
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0); // baseline
        tracker.tickDirect(session, playerUuid, 0.1, 0.0, 0.0); // delta = 0.1 < 0.5 threshold
        assertFalse(eventsFor(session).stream().anyMatch(e -> "velocity_change".equals(e.type())));
    }

    @Test
    void emitsEventAboveThreshold() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.1, 1);
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0); // baseline
        tracker.tickDirect(session, playerUuid, 1.0, 0.0, 0.0); // delta ≈ 1.0 > 0.1 threshold

        List<ReplayEvent> velEvents = eventsFor(session).stream()
                .filter(e -> "velocity_change".equals(e.type()))
                .toList();
        assertEquals(1, velEvents.size());
        assertEquals(playerUuid, velEvents.get(0).actorUuid());
        assertEquals(1.0, (double) velEvents.get(0).data().get("vx"), 0.001);
    }

    @Test
    void intervalThrottlesSampling() {
        // intervalTicks=2: only sample on ticks where count%2==0 (ticks 2, 4, …)
        VelocityTracker tracker = new VelocityTracker(recorder, 0.0, 2);

        // tick 1. skipped (1%2 != 0)
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0);
        // tick 2. sampled, stores baseline (no prev)
        tracker.tickDirect(session, playerUuid, 1.0, 0.0, 0.0);
        // tick 3. skipped
        tracker.tickDirect(session, playerUuid, 2.0, 0.0, 0.0);
        // tick 4. sampled, prev = 1.0 → delta = 2.0 - 1.0 = 1.0 ≥ 0.0 → event fired
        tracker.tickDirect(session, playerUuid, 3.0, 0.0, 0.0);

        long velEventCount = eventsFor(session).stream()
                .filter(e -> "velocity_change".equals(e.type()))
                .count();
        assertEquals(1, velEventCount, "Exactly one velocity_change event expected");
    }

    @Test
    void clearRemovesEntry() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.1, 1);
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0);
        assertEquals(1, tracker.trackedCount());
        tracker.clear(playerUuid);
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void clearAllRemovesAllEntries() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.1, 1);
        UUID other = UUID.randomUUID();
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0);
        tracker.tickDirect(session, other, 0.0, 0.0, 0.0);
        assertEquals(2, tracker.trackedCount());
        tracker.clearAll();
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void evictInactiveRemovesStaleEntries() {
        VelocityTracker tracker = new VelocityTracker(recorder, 0.1, 1);
        UUID other = UUID.randomUUID();
        tracker.tickDirect(session, playerUuid, 0.0, 0.0, 0.0);
        tracker.tickDirect(session, other, 0.0, 0.0, 0.0);
        assertEquals(2, tracker.trackedCount());

        int evicted = tracker.evictInactive(Set.of(playerUuid));
        assertEquals(1, evicted, "Should evict exactly the one inactive player");
        assertEquals(1, tracker.trackedCount());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ReplayEvent> eventsFor(BattleSession s) {
        try {
            Field f = BattleReplayRecorder.class.getDeclaredField("events");
            f.setAccessible(true);
            Map<UUID, List<ReplayEvent>> events = (Map<UUID, List<ReplayEvent>>) f.get(recorder);
            return events.getOrDefault(s.getUuid(), List.of());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
