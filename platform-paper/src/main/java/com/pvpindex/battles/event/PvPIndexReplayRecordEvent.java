package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.ReplayEvent;

public class PvPIndexReplayRecordEvent extends AbstractPvPIndexBattleEvent {
    private final ReplayEvent replayEvent;

    public PvPIndexReplayRecordEvent(BattleSession session, ReplayEvent replayEvent) {
        super(session);
        this.replayEvent = replayEvent;
    }

    public ReplayEvent getReplayEvent() {
        return replayEvent;
    }
}
