package com.pvpindex.battles.event;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.GameModeType;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;

/**
 * Base class for all PvPIndex battle events. Subclasses must declare their
 * own {@code HandlerList}. Bukkit requires one per concrete event type for
 * {@code @EventHandler} filtering to work correctly.
 */
public abstract class AbstractPvPIndexBattleEvent extends Event {
    private final BattleSession session;

    protected AbstractPvPIndexBattleEvent(BattleSession session) {
        this.session = session;
    }

    public BattleSession getSession() {
        return session;
    }

    public UUID getBattleUuid() {
        return session.getUuid();
    }

    public List<BattleParticipant> getParticipants() {
        return session.getParticipants();
    }

    public GameModeType getGameMode() {
        return session.getGameMode();
    }
}
