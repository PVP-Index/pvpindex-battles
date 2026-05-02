package com.pvpindex.battles.arena;

import java.util.List;

public record Arena(
        String id,
        String name,
        String world,
        List<SpawnPoint> spawnPoints,
        SpawnPoint spectatorSpawn,
        boolean enabled
) {}
