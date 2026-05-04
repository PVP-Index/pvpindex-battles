package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a new battle session is created but before it becomes active.
 * Cancelling this event prevents the battle from being registered.
 */
public class PvPIndexBattleCreateEvent extends AbstractPvPIndexBattleEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    public PvPIndexBattleCreateEvent(BattleSession session) {
        super(session);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
