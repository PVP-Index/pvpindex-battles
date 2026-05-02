package com.pvpindex.velocity.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry tracking which battles are active and on which backend.
 *
 * <p>The proxy maintains this registry so it can:</p>
 * <ul>
 *   <li>Detect when a battling player server-switches and alert the backend.</li>
 *   <li>Answer cross-server queries ("is player X in a battle and where?").</li>
 *   <li>Display live battle counts via the proxy command.</li>
 * </ul>
 */
public final class BattleRegistry {

    /**
     * Lightweight snapshot of an active battle held by the proxy.
     *
     * @param battleUuid    Unique battle ID (matches the Paper plugin).
     * @param serverName    Name of the backend that owns this battle.
     * @param participantUuids Set of all participant player UUIDs.
     * @param status        Last known status string (e.g. "IN_PROGRESS").
     */
    public record BattleEntry(
            UUID battleUuid,
            String serverName,
            Set<UUID> participantUuids,
            String status
    ) {}

    private final ConcurrentHashMap<UUID, BattleEntry> battles = new ConcurrentHashMap<>();
    /** Reverse lookup: participant → battle UUID for O(1) mid-battle server-switch checks. */
    private final ConcurrentHashMap<UUID, UUID> playerToBattle = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    /** Register a new battle received from a Paper backend. */
    public void register(UUID battleUuid, String serverName, Set<UUID> participants, String status) {
        BattleEntry entry = new BattleEntry(battleUuid, serverName, Set.copyOf(participants), status);
        battles.put(battleUuid, entry);
        for (UUID p : participants) {
            playerToBattle.put(p, battleUuid);
        }
    }

    /** Remove a battle and clean up participant mappings. */
    public void remove(UUID battleUuid) {
        BattleEntry entry = battles.remove(battleUuid);
        if (entry != null) {
            entry.participantUuids().forEach(playerToBattle::remove);
        }
    }

    /** Add a single participant to an existing battle. */
    public void addParticipant(UUID battleUuid, UUID playerUuid) {
        battles.computeIfPresent(battleUuid, (k, existing) -> {
            Set<UUID> updated = new java.util.HashSet<>(existing.participantUuids());
            updated.add(playerUuid);
            return new BattleEntry(k, existing.serverName(), Set.copyOf(updated), existing.status());
        });
        playerToBattle.put(playerUuid, battleUuid);
    }

    /** Remove a participant from a battle (without removing the battle). */
    public void removeParticipant(UUID playerUuid) {
        UUID battleUuid = playerToBattle.remove(playerUuid);
        if (battleUuid == null) return;
        battles.computeIfPresent(battleUuid, (k, existing) -> {
            Set<UUID> updated = new java.util.HashSet<>(existing.participantUuids());
            updated.remove(playerUuid);
            return new BattleEntry(k, existing.serverName(), Set.copyOf(updated), existing.status());
        });
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** Returns the battle this player is currently participating in, or empty. */
    public Optional<BattleEntry> getBattleForPlayer(UUID playerUuid) {
        UUID battleUuid = playerToBattle.get(playerUuid);
        return battleUuid == null ? Optional.empty() : Optional.ofNullable(battles.get(battleUuid));
    }

    /** Returns the battle with the given UUID, or empty. */
    public Optional<BattleEntry> get(UUID battleUuid) {
        return Optional.ofNullable(battles.get(battleUuid));
    }

    /** Returns all battles currently hosted on the given backend server. */
    public List<BattleEntry> getBattlesOnServer(String serverName) {
        return battles.values().stream()
                .filter(e -> e.serverName().equals(serverName))
                .collect(Collectors.toList());
    }

    /** Snapshot of all active battles (unmodifiable view). */
    public Collection<BattleEntry> all() {
        return Collections.unmodifiableCollection(battles.values());
    }

    /** Total number of battles currently tracked. */
    public int size() {
        return battles.size();
    }

    /** Returns true if this player is currently in any battle. */
    public boolean isInBattle(UUID playerUuid) {
        return playerToBattle.containsKey(playerUuid);
    }
}
