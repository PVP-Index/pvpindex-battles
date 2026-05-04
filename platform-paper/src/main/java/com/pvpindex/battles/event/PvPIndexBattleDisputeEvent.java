package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import org.bukkit.event.HandlerList;

/**
 * Fired when a battle is disputed (e.g. early disconnect, suspected abuse).
 */
public class PvPIndexBattleDisputeEvent extends AbstractPvPIndexBattleEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String reason;

    public PvPIndexBattleDisputeEvent(BattleSession session, String reason) {
        super(session);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
