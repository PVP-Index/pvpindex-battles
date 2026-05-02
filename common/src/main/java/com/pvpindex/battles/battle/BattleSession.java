package com.pvpindex.battles.battle;

import com.pvpindex.battles.battle.type.BattleStatus;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattleSession {
    private final UUID uuid;
    private final BattleType battleType;
    private final GameModeType gameMode;
    private final String serverId;
    private final String arenaId;
    private BattleStatus status;
    private final List<BattleParticipant> participants;
    private Instant startedAt;
    private Instant endedAt;
    private final List<UUID> winners;
    private final List<UUID> losers;
    private final Map<String, Object> metadata;

    public BattleSession(UUID uuid, BattleType battleType, GameModeType gameMode, String serverId, String arenaId) {
        this.uuid = uuid;
        this.battleType = battleType;
        this.gameMode = gameMode;
        this.serverId = serverId;
        this.arenaId = arenaId;
        this.status = BattleStatus.CREATED;
        this.participants = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.losers = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public UUID getUuid() { return uuid; }
    public BattleType getBattleType() { return battleType; }
    public GameModeType getGameMode() { return gameMode; }
    /** Derived convenience for UX — returns the lowercased enum name. */
    public String getGameModeId() { return gameMode != null ? gameMode.name().toLowerCase() : ""; }
    public String getServerId() { return serverId; }
    public String getArenaId() { return arenaId; }
    public BattleStatus getStatus() { return status; }
    public List<BattleParticipant> getParticipants() { return participants; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public List<UUID> getWinners() { return winners; }
    public List<UUID> getLosers() { return losers; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void addParticipant(BattleParticipant participant) {
        participants.add(participant);
    }

    public void setStatus(BattleStatus status) {
        this.status = status;
    }

    public void markStarted() {
        this.startedAt = Instant.now();
        this.status = BattleStatus.ACTIVE;
    }

    public void markFinished() {
        this.endedAt = Instant.now();
        this.status = BattleStatus.FINISHED;
    }

    public long durationSeconds() {
        if (startedAt == null || endedAt == null) {
            return 0L;
        }
        return Math.max(0L, endedAt.getEpochSecond() - startedAt.getEpochSecond());
    }
}
