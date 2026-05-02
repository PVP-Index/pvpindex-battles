package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;

public class PvPIndexBattleFinishEvent extends AbstractPvPIndexBattleEvent {
    public PvPIndexBattleFinishEvent(BattleSession session) {
        super(session);
    }
}
