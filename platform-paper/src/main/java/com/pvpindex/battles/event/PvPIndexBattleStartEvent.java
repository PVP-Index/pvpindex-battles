package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;

public class PvPIndexBattleStartEvent extends AbstractPvPIndexBattleEvent {
    public PvPIndexBattleStartEvent(BattleSession session) {
        super(session);
    }
}
