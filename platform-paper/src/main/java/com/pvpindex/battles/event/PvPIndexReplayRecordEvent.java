package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.ReplayEvent;
import org.bukkit.event.HandlerList;

/**
 * Fired each time a replay event (frame) is recorded during a battle.
 */
public class PvPIndexReplayRecordEvent extends AbstractPvPIndexBattleEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ReplayEvent replayEvent;

    public PvPIndexReplayRecordEvent(BattleSession session, ReplayEvent replayEvent) {
        super(session);
        this.replayEvent = replayEvent;
    }

    public ReplayEvent getReplayEvent() {
        return replayEvent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
