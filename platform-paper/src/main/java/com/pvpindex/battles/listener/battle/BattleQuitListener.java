package com.pvpindex.battles.listener.battle;

import static com.pvpindex.battles.listener.battle.BattleListenerHelper.contains;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.SmpLootPhaseService;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class BattleQuitListener implements Listener {
    private final BattleService battleService;
    private final BattleReplayRecorder replayRecorder;
    private BattleQueueService queueService;
    private SmpLootPhaseService smpLootPhaseService;

    public BattleQuitListener(BattleService battleService, BattleReplayRecorder replayRecorder) {
        this.battleService = battleService;
        this.replayRecorder = replayRecorder;
    }

    public void setQueueService(BattleQueueService queueService) {
        this.queueService = queueService;
    }

    public void setSmpLootPhaseService(SmpLootPhaseService smpLootPhaseService) {
        this.smpLootPhaseService = smpLootPhaseService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (queueService != null) {
            queueService.leaveQuietly(playerUuid);
        }
        BattleSession session = battleService.findActiveBattleFor(playerUuid).orElse(null);
        if (session == null || !contains(session, playerUuid)) return;
        replayRecorder.record(session, "player_leave", playerUuid, null, Map.of("reason", "quit"));

        UUID battleUuid = session.getUuid();

        // If the quitting player is the winner in an active SMP loot phase,
        // end the loot phase cleanly (restores loser, cancels boss bar,
        // etc.) instead of calling endAndCleanup directly.
        if (smpLootPhaseService != null && smpLootPhaseService.isInLootPhase(battleUuid)) {
            battleService.markPlayerLeftEarly(battleUuid, playerUuid);
            smpLootPhaseService.endLootPhase(battleUuid);
            return;
        }

        battleService.markPlayerLeftEarly(battleUuid, playerUuid);

        List<UUID> winners = new ArrayList<>();
        for (BattleParticipant p : session.getParticipants()) {
            if (!p.getUuid().equals(playerUuid)) winners.add(p.getUuid());
        }
        battleService.endAndCleanup(battleUuid, winners);
    }
}
