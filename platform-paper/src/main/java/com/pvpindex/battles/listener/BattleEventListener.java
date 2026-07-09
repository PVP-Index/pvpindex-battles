package com.pvpindex.battles.listener;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.SmpLootPhaseService;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.List;
import org.bukkit.event.Listener;

/**
 * Backward-compatible facade that delegates to the split listeners in
 * {@link com.pvpindex.battles.listener.battle}. New code should use
 * {@link com.pvpindex.battles.listener.battle.BattleEventListeners} directly.
 */
@Deprecated
public class BattleEventListener implements Listener {
    private final List<Listener> delegates;

    public BattleEventListener(BattleService battleService, BattleReplayRecorder replayRecorder, GameModeRegistry gameModeRegistry) {
        this.delegates = com.pvpindex.battles.listener.battle.BattleEventListeners.create(
                battleService, replayRecorder, gameModeRegistry);
    }

    /** Back-compat constructor for code that doesn't supply a registry. */
    public BattleEventListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this(battleService, replayRecorder, null);
    }

    public void setQueueService(BattleQueueService queueService) {
        com.pvpindex.battles.listener.battle.BattleEventListeners.setQueueService(delegates, queueService);
    }

    public void setSmpLootPhaseService(SmpLootPhaseService smpLootPhaseService) {
        com.pvpindex.battles.listener.battle.BattleEventListeners.setSmpLootPhaseService(delegates, smpLootPhaseService);
    }

    public List<Listener> getDelegates() {
        return delegates;
    }

    public int evictStalePlayers(java.util.Collection<com.pvpindex.battles.battle.BattleSession> activeSessions) {
        var tracker = com.pvpindex.battles.listener.battle.BattleEventListeners.swingMissTracker(delegates);
        return tracker != null ? tracker.evictStalePlayers(activeSessions) : 0;
    }
}
