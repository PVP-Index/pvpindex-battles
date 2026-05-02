package com.pvpindex.battles.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Empty/void chunk generator used by {@link ProceduralArenaStrategy} so the
 * pool can spin up arena worlds with zero terrain — the arena itself is
 * placed by the strategy after the world loads.
 */
public final class VoidWorldGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // intentionally empty — leaves the chunk full of air
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }
    @Override
    public boolean shouldGenerateSurface() { return false; }
    @Override
    public boolean shouldGenerateCaves() { return false; }
    @Override
    public boolean shouldGenerateDecorations() { return false; }
    @Override
    public boolean shouldGenerateMobs() { return false; }
    @Override
    public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0, 70, 0);
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of();
    }
}
