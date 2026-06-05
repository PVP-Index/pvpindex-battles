package com.pvpindex.battles.placeholder;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.ParticipantResult;
import com.pvpindex.battles.event.PvPIndexBattleFinishEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Keeps {@link PlayerStatCache} up-to-date by listening to
 * {@link PvPIndexBattleFinishEvent} and writing each participant's post-battle
 * elo and win/loss/draw outcome.
 */
public final class PlaceholderUpdateListener implements Listener {

    private final PlayerStatCache cache;

    public PlaceholderUpdateListener(PlayerStatCache cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onBattleFinish(PvPIndexBattleFinishEvent event) {
        BattleSession session = event.getSession();
        String modeId = session.getGameMode().name(); // e.g. "SWORD"

        for (BattleParticipant p : session.getParticipants()) {
            cache.updateFromBattle(p.getUuid(), modeId, p.getEloAfter(), p.getEloChange());

            PlayerStatCache.Entry entry = cache.getOrCreate(p.getUuid());
            switch (p.getResult()) {
                case WIN        -> entry.wins.incrementAndGet();
                case LOSS       -> entry.losses.incrementAndGet();
                case DRAW       -> entry.draws.incrementAndGet();
                default         -> { /* ELIMINATED / LEFT / UNKNOWN. not counted */ }
            }
        }
    }
}
