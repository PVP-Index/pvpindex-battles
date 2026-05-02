package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;

public class PvPIndexBattleSubmitEvent extends AbstractPvPIndexBattleEvent {
    public PvPIndexBattleSubmitEvent(BattleSession session) {
        super(session);
    }
}
