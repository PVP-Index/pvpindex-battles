package com.pvpindex.battles.listener.battle;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks arm swings and pending misses for anti-cheat / replay purposes.
 * Shared between {@link BattleDamageListener} and {@link BattleArmSwingListener}.
 */
public class SwingMissTracker {
    // Last arm-swing timestamp per player (for swing-interval / autoclicker detection).
    private final Map<UUID, Long> lastSwingMs = new ConcurrentHashMap<>();
    // Pending miss detection: entry present means a swing happened but no hit has been
    // confirmed yet within the current server tick. Value = battleUuid.
    private final Map<UUID, UUID> pendingMiss = new ConcurrentHashMap<>();

    public Long recordSwing(UUID playerUuid) {
        return lastSwingMs.put(playerUuid, System.currentTimeMillis());
    }

    public long msSinceLastSwing(UUID playerUuid) {
        Long prev = lastSwingMs.get(playerUuid);
        return prev == null ? -1L : System.currentTimeMillis() - prev;
    }

    public void setPendingMiss(UUID playerUuid, UUID battleUuid) {
        pendingMiss.put(playerUuid, battleUuid);
    }

    public UUID removePendingMiss(UUID playerUuid) {
        return pendingMiss.remove(playerUuid);
    }

    public boolean hasPendingMiss(UUID playerUuid) {
        return pendingMiss.containsKey(playerUuid);
    }

    /**
     * Removes tracking entries for players who are no longer participants in
     * any active battle. Returns the number of evicted entries.
     */
    public int evictStalePlayers(Collection<BattleSession> activeSessions) {
        Collection<UUID> activePlayers = activeSessions.stream()
                .flatMap(s -> s.getParticipants().stream().map(BattleParticipant::getUuid))
                .toList();

        int evicted = 0;
        for (UUID uuid : new ArrayList<>(lastSwingMs.keySet())) {
            if (!activePlayers.contains(uuid)) {
                lastSwingMs.remove(uuid);
                pendingMiss.remove(uuid);
                evicted++;
            }
        }
        return evicted;
    }
}
