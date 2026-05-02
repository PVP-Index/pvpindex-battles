package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;

public class PvPIndexBattleCreateEvent extends AbstractPvPIndexBattleEvent {
    public PvPIndexBattleCreateEvent(BattleSession session) {
        super(session);
    }
}
