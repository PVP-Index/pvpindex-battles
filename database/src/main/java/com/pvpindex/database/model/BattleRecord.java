package com.pvpindex.database.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BattleRecord(
        UUID battleId, List<UUID> participants, UUID winnerId,
        String modeId, long durationMs, Instant timestamp, String serverName
) {}
