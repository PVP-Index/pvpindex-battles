package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.util.List;

/**
 * A world template the {@link WorldGeneratorService} can stamp out as fresh
 * arena instances on demand. Either a directory of an existing template
 * world (for {@link WorldCopyStrategy}) or a {@code .schem}/{@code .schematic}
 * file pasted into a configured host world (for {@link SchematicStrategy}).
 *
 * @param sourcePath relative path inside {@code plugins/PvPIndexBattles/templates/}
 */
public record ArenaTemplate(
        String id,
        String displayName,
        String strategy,
        String sourcePath,
        String hostWorld,
        List<SpawnPoint> spawnPoints,
        SpawnPoint spectatorSpawn,
        SpawnPoint pasteOrigin
) {}
