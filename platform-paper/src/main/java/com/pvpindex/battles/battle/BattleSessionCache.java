package com.pvpindex.battles.battle;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O(1) player-to-active-battle index. A player can only be in one active
 * battle at a time, so lookups by UUID are constant time.
 */
public class BattleSessionCache {
    private final Map<UUID, BattleSession> playerIndex = new ConcurrentHashMap<>();

    public void register(BattleSession session) {
        for (BattleParticipant participant : session.getParticipants()) {
            playerIndex.put(participant.getUuid(), session);
        }
    }

    public void unregister(BattleSession session) {
        for (BattleParticipant participant : session.getParticipants()) {
            // Only remove if this session still owns the mapping (defensive:
            // prevents a slow cleanup from wiping a newer battle for the same player).
            playerIndex.remove(participant.getUuid(), session);
        }
    }

    public Optional<BattleSession> findForPlayer(UUID playerUuid) {
        return Optional.ofNullable(playerIndex.get(playerUuid));
    }

    public boolean hasBattle(UUID playerUuid) {
        return playerIndex.containsKey(playerUuid);
    }
}
