package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleSession;
import org.bukkit.event.HandlerList;

/**
 * Fired when a battle finishes (winners/losers determined, status set to
 * FINISHED). Not cancellable. the battle has already concluded.
 */
public class PvPIndexBattleFinishEvent extends AbstractPvPIndexBattleEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PvPIndexBattleFinishEvent(BattleSession session) {
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
