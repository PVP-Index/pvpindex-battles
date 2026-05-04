package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a battle transitions to the active/countdown phase.
 * Cancelling this event aborts the battle entirely. The optional
 * {@link #setStartMessage(Component)} allows listeners to override the
 * message shown to participants when the battle begins.
 */
public class PvPIndexBattleStartEvent extends AbstractPvPIndexBattleEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private Component startMessage;

    public PvPIndexBattleStartEvent(BattleSession session) {
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

    /** Returns the custom start message, or {@code null} if unchanged. */
    public Component getStartMessage() {
        return startMessage;
    }

    /** Override the message broadcast to participants when the battle starts. */
    public void setStartMessage(Component startMessage) {
        this.startMessage = startMessage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
