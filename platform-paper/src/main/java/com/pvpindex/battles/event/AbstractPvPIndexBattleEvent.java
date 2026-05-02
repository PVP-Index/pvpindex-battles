package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public abstract class AbstractPvPIndexBattleEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BattleSession session;

    protected AbstractPvPIndexBattleEvent(BattleSession session) {
        this.session = session;
    }

    public BattleSession getSession() {
        return session;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
