package com.pvpindex.battles.practice;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of an active practice session.
 * Created when a player enters any practice mode and discarded when they leave.
 */
public final class PracticeSession {

    private final UUID playerUuid;
    private final PracticeMode mode;
    /** The game-mode kit ID used for this session (e.g. {@code "sword_starter"}). May be null for default. */
    private final String gameModeId;
    private final Instant startedAt;

    public PracticeSession(UUID playerUuid, PracticeMode mode, String gameModeId) {
        this.playerUuid = playerUuid;
        this.mode = mode;
        this.gameModeId = gameModeId;
        this.startedAt = Instant.now();
    }

    public UUID playerUuid() { return playerUuid; }
    public PracticeMode mode() { return mode; }
    public String gameModeId() { return gameModeId; }
    public Instant startedAt() { return startedAt; }
}
