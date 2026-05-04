package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import org.bukkit.event.HandlerList;

/**
 * Fired after a battle result is successfully submitted to the PvPIndex API.
 */
public class PvPIndexBattleSubmitEvent extends AbstractPvPIndexBattleEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PvPIndexBattleSubmitEvent(BattleSession session) {
        super(session);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
