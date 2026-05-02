package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;

public class PvPIndexBattleDisputeEvent extends AbstractPvPIndexBattleEvent {
    private final String reason;

    public PvPIndexBattleDisputeEvent(BattleSession session, String reason) {
        super(session);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
