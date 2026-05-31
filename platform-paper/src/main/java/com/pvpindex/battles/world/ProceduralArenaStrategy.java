package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Builds a small symmetrical PvP arena in a fresh void world, in code, with
 * no asset files. Designed to make {@code Sword Duel} playable out of the
 * box — drop the plugin into a server and the {@code arena_duel} template
 * "just works" without shipping or generating template world directories.
 *
 * <h3>Arena layout</h3>
 * <pre>
 *   - 21x21 stone-brick floor at Y=64
 *   - 1-block stone-brick perimeter wall + 4 blocks of glass above (so
 *     spectators can see in but players cannot escape)
 *   - Two iron-block 3x3 spawn pads at the east + west edges, facing each
 *     other across the arena
 *   - World gamerules tuned for PvP (no day/night cycle, no mob spawning,
 *     no weather, etc.)
 * </pre>
 *
 * <p>Cleanup is identical to {@link WorldCopyStrategy}: the world is
 * unloaded and its folder deleted. Because arena world names use the
 * standard {@code pvpindex_*} prefix, the existing
 * {@link ArenaPoolService#sweepOrphans()} reaper handles crash recovery
 * automatically.</p>
 *
 * <p><b>Threading:</b> {@link Bukkit#createWorld(WorldCreator)} and block
 * mutation must run on the main thread; {@link ArenaPoolService} ensures
 * non-{@code copy} strategies are warmed on the main thread.</p>
 */
public final class ProceduralArenaStrategy implements WorldGenerationStrategy {

    private static final int FLOOR_Y = 64;
    private static final int RADIUS = 10;       // → 21x21 floor
    private static final int WALL_HEIGHT = 5;   // 1 stone + 4 glass

    private final Plugin plugin;

    public ProceduralArenaStrategy(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() { return "procedural"; }

    @Override
    public ArenaInstance generate(ArenaTemplate template) throws IOException {
        UUID id = UUID.randomUUID();
        String worldName = "pvpindex_" + template.id() + "_" + id.toString().substring(0, 8);

        if (Bukkit.getServer() == null) {
            // Tests: no real Bukkit available — return a placeholder.
            return new ArenaInstance(id, template.id(), worldName,
                    spawnsFor(template), template.spectatorSpawn());
        }

        // NOTE: deliberately use WorldType.NORMAL (not FLAT) — FLAT makes Paper
        // try to parse flat-generator settings JSON (expects a "layers" key)
        // even when a custom ChunkGenerator is supplied, producing a harmless
        // but noisy "No key layers in MapLike[{}]" error on every world create.
        WorldCreator creator = new WorldCreator(worldName)
                .generator(new ArenaChunkGenerator())
                .type(WorldType.NORMAL)
                .generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IOException("Failed to create arena world: " + worldName);
        }

        // Arena structure is embedded in the ChunkGenerator — spawn chunks are
        // already fully built when createWorld() returns. Only game rules remain.
        configureWorld(world);

        plugin.getLogger().info("Built procedural arena " + worldName + " from template " + template.id());
        return new ArenaInstance(id, template.id(), world.getName(),
                spawnsFor(template), template.spectatorSpawn());
    }

    @Override
    public void release(ArenaInstance instance) {
        // Pool service handles unload+delete via the generic pvpindex_* path
        // (see ArenaPoolService.unloadAndDelete). Nothing else to do here.
    }

    // -------------------------------------------------------------------------
    // Chunk generator — embeds the full arena structure during world creation
    // -------------------------------------------------------------------------

    /**
     * Custom {@link ChunkGenerator} that places the duel arena's floor and walls
     * in {@code generateSurface()} so the structure is ready the moment
     * {@link Bukkit#createWorld(WorldCreator)} returns — no separate block-loop
     * pass needed. All other terrain passes are suppressed (void world).
     */
    private static final class ArenaChunkGenerator extends ChunkGenerator {

        @Override
        public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                    int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
            // Clip the arena footprint to this chunk's block range.
            int blockMinX = chunkX * 16;
            int blockMinZ = chunkZ * 16;
            int startX = Math.max(-RADIUS, blockMinX);
            int endX   = Math.min( RADIUS, blockMinX + 15);
            int startZ = Math.max(-RADIUS, blockMinZ);
            int endZ   = Math.min( RADIUS, blockMinZ + 15);
            if (startX > endX || startZ > endZ) return;

            for (int wx = startX; wx <= endX; wx++) {
                int lx = wx - blockMinX;
                for (int wz = startZ; wz <= endZ; wz++) {
                    int lz = wz - blockMinZ;
                    boolean perimeter = (wx == -RADIUS || wx == RADIUS || wz == -RADIUS || wz == RADIUS);
                    boolean spawnPad  = isSpawnPad(wx, wz);
                    // Floor
                    chunkData.setBlock(lx, FLOOR_Y, lz,
                            spawnPad ? Material.IRON_BLOCK : Material.STONE_BRICKS);
                    // Perimeter wall: 1 stone-brick base + glass courses above
                    if (perimeter) {
                        chunkData.setBlock(lx, FLOOR_Y + 1, lz, Material.STONE_BRICKS);
                        for (int dy = 2; dy <= WALL_HEIGHT; dy++) {
                            chunkData.setBlock(lx, FLOOR_Y + dy, lz, Material.GLASS);
                        }
                    }
                }
            }
        }

        private static boolean isSpawnPad(int x, int z) {
            if (z < -1 || z > 1) return false;
            return (x >= -RADIUS && x <= -RADIUS + 2) || (x >= RADIUS - 2 && x <= RADIUS);
        }

        @Override public boolean shouldGenerateNoise()       { return false; }
        @Override public boolean shouldGenerateSurface()     { return false; }
        @Override public boolean shouldGenerateCaves()       { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs()        { return false; }
        @Override public boolean shouldGenerateStructures()  { return false; }

        @Override
        public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 0, FLOOR_Y + 1, 0);
        }

        @Override
        public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private void configureWorld(World world) {
        world.setSpawnLocation(0, FLOOR_Y + 1, 0);
        world.setDifficulty(Difficulty.NORMAL);
        // PvP must be explicitly enabled — the default for a fresh world is false,
        // and WorldGuard (+ other protection plugins) respect world.isPVP().
        world.setPVP(true);
        applyRule(world, "doDaylightCycle", false);
        applyRule(world, "doWeatherCycle", false);
        applyRule(world, "doMobSpawning", false);
        applyRule(world, "doFireTick", false);
        applyRule(world, "mobGriefing", false);
        applyRule(world, "keepInventory", false);
        applyRule(world, "announceAdvancements", false);
        applyRule(world, "showDeathMessages", false);
        world.setTime(6000L); // noon
        world.setStorm(false);
        world.setThundering(false);
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyRule(World world, String name, T value) {
        GameRule<T> rule = (GameRule<T>) GameRule.getByName(name);
        if (rule != null) world.setGameRule(rule, value);
    }


    /**
     * Falls back to two sensible default spawns when the template doesn't
     * declare any — matches the iron pad positions built above. Keeps
     * {@code arena_duel} playable even with a stripped-down templates.yml.
     */
    private List<SpawnPoint> spawnsFor(ArenaTemplate template) {
        if (template.spawnPoints() != null && template.spawnPoints().size() >= 2) {
            return template.spawnPoints();
        }
        // MC yaw: 0=south, 90=west, 180=north, -90=east.
        return List.of(
                new SpawnPoint(-(RADIUS - 1), FLOOR_Y + 1.0, 0, -90f, 0f), // facing east
                new SpawnPoint( (RADIUS - 1), FLOOR_Y + 1.0, 0,  90f, 0f)  // facing west
        );
    }
}
