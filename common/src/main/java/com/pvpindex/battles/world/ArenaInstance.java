package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.util.List;
import java.util.UUID;

/**
 * A live, single-use arena produced from an {@link ArenaTemplate}. The
 * generator hands one of these to {@code BattleService} when a battle is
 * created, and reclaims it (deletes copied world / clears pasted region)
 * after the battle ends.
 */
public record ArenaInstance(
        UUID instanceId,
        String templateId,
        String worldName,
        List<SpawnPoint> spawnPoints,
        SpawnPoint spectatorSpawn
) {}
