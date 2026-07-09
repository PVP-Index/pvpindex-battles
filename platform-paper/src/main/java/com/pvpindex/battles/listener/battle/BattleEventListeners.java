package com.pvpindex.battles.listener.battle;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.SmpLootPhaseService;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import java.util.List;
import org.bukkit.event.Listener;

/**
 * Factory that creates and wires all split battle event listeners.
 * Keeps the bootstrap code simple and ensures shared state (swing/miss tracker)
 * is created once and passed to the listeners that need it.
 */
public final class BattleEventListeners {
    private BattleEventListeners() {}

    public static List<Listener> create(BattleService battleService, BattleReplayRecorder replayRecorder, GameModeRegistry gameModeRegistry) {
        SwingMissTracker swingMissTracker = new SwingMissTracker();
        return List.of(
                new BattleDamageListener(battleService, replayRecorder, swingMissTracker),
                new BattleItemConsumeListener(battleService, replayRecorder),
                new BattleInteractListener(battleService),
                new BattleBlockBreakListener(battleService, gameModeRegistry),
                new BattleBlockPlaceListener(battleService, gameModeRegistry),
                new BattleExplosionListener(battleService, gameModeRegistry),
                new BattleDeathListener(battleService, replayRecorder, gameModeRegistry),
                new BattleHealListener(battleService, replayRecorder),
                new BattleQuitListener(battleService, replayRecorder),
                new BattleArmSwingListener(battleService, replayRecorder, swingMissTracker),
                new BattleJumpListener(battleService, replayRecorder),
                new BattleSprintListener(battleService, replayRecorder),
                new BattleSneakListener(battleService, replayRecorder),
                new BattleMoveListener(battleService, replayRecorder),
                new BattleProjectileLaunchListener(battleService),
                new BattleShootBowListener(battleService),
                new BattleProjectileHitListener(battleService, replayRecorder),
                new BattleEntityDeathListener(battleService, replayRecorder)
        );
    }

    public static void setQueueService(List<Listener> listeners, BattleQueueService queueService) {
        for (Listener listener : listeners) {
            if (listener instanceof BattleQuitListener quitListener) {
                quitListener.setQueueService(queueService);
            }
        }
    }

    public static void setSmpLootPhaseService(List<Listener> listeners, SmpLootPhaseService smpLootPhaseService) {
        for (Listener listener : listeners) {
            if (listener instanceof BattleDeathListener deathListener) {
                deathListener.setSmpLootPhaseService(smpLootPhaseService);
            } else if (listener instanceof BattleQuitListener quitListener) {
                quitListener.setSmpLootPhaseService(smpLootPhaseService);
            }
        }
    }

    public static SwingMissTracker swingMissTracker(List<Listener> listeners) {
        for (Listener listener : listeners) {
            if (listener instanceof BattleDamageListener damageListener) {
                return damageListener.swingMissTracker();
            }
        }
        return null;
    }
}
