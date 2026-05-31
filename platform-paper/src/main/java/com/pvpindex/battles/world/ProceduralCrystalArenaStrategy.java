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
 * Builds a Crystal PvP arena in a fresh void world, in code, with no asset
 * files. An obsidian floor is required so players can place end crystals.
 *
 * <h3>Arena layout</h3>
 * <pre>
 *   - 23x23 obsidian floor at Y=64 (crystals must be placed on obsidian
 *     or bedrock)
 *   - 2-block obsidian perimeter wall + 5 blocks of glass above (taller
 *     than the duel arena so crystal explosions stay contained)
 *   - Two obsidian 3x3 spawn pads at the east + west edges
 *   - Natural regeneration OFF
 * </pre>
 */
public final class ProceduralCrystalArenaStrategy implements WorldGenerationStrategy {

	private static final int FLOOR_Y = 64;
	private static final int RADIUS = 11;
	private static final int WALL_BASE = 2;
	private static final int WALL_GLASS = 5;

	private final Plugin plugin;

	public ProceduralCrystalArenaStrategy(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String id() { return "procedural_crystal"; }

	@Override
	public ArenaInstance generate(ArenaTemplate template) throws IOException {
		UUID id = UUID.randomUUID();
		String worldName = "pvpindex_" + template.id() + "_" + id.toString().substring(0, 8);

		if (Bukkit.getServer() == null) {
			return new ArenaInstance(id, template.id(), worldName,
					spawnsFor(template), template.spectatorSpawn());
		}

		WorldCreator creator = new WorldCreator(worldName)
				.generator(new CrystalArenaChunkGenerator())
				.type(WorldType.NORMAL)
				.generateStructures(false);
		World world = Bukkit.createWorld(creator);
		if (world == null) {
			throw new IOException("Failed to create crystal arena world: " + worldName);
		}

		// Arena structure is embedded in the ChunkGenerator — spawn chunks are
		// already fully built when createWorld() returns. Only game rules remain.
		configureWorld(world);

		// Pre-load every chunk the arena footprint touches. On Paper, spawn-area
		// chunks are already generated during createWorld(). On Spigot and other
		// Bukkit implementations only the chunk at the world spawn may be ready —
		// explicitly calling getChunkAt() triggers generateSurface() for any
		// remaining chunks, guaranteeing the arena is fully built on all server types.
		int minC = Math.floorDiv(-RADIUS, 16);
		int maxC = Math.floorDiv( RADIUS, 16);
		for (int cx = minC; cx <= maxC; cx++) {
			for (int cz = minC; cz <= maxC; cz++) {
				world.getChunkAt(cx, cz);
			}
		}

		plugin.getLogger().info("Built procedural crystal arena " + worldName + " from template " + template.id());
		return new ArenaInstance(id, template.id(), world.getName(),
				spawnsFor(template), template.spectatorSpawn());
	}

	@Override
	public void release(ArenaInstance instance) {
		// ArenaPoolService handles unload + folder deletion for all pvpindex_* worlds.
	}

	private void configureWorld(World world) {
		world.setSpawnLocation(0, FLOOR_Y + 1, 0);
		world.setDifficulty(Difficulty.NORMAL);
		world.setPVP(true);
		applyRule(world, "doDaylightCycle", false);
		applyRule(world, "doWeatherCycle", false);
		applyRule(world, "doMobSpawning", false);
		applyRule(world, "doFireTick", false);
		applyRule(world, "mobGriefing", false);
		applyRule(world, "keepInventory", false);
		applyRule(world, "announceAdvancements", false);
		applyRule(world, "showDeathMessages", false);
		world.setTime(6000L);
		world.setStorm(false);
		world.setThundering(false);
		// Constrain the world border to just outside the arena so the server never
		// generates chunks beyond the arena footprint. Works on all Bukkit implementations.
		// setWarningDistance(0) suppresses the red vignette inside the arena walls.
		world.getWorldBorder().setCenter(0, 0);
		world.getWorldBorder().setSize(RADIUS * 2 + 1 + 48.0);
		world.getWorldBorder().setWarningDistance(0);
	}

	@SuppressWarnings("unchecked")
	private static <T> void applyRule(World world, String name, T value) {
		GameRule<T> rule = (GameRule<T>) GameRule.getByName(name);
		if (rule != null) world.setGameRule(rule, value);
	}

	// -------------------------------------------------------------------------
	// Chunk generator
	// -------------------------------------------------------------------------

	private static final class CrystalArenaChunkGenerator extends ChunkGenerator {

		@Override
		public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
									int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
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
					// Floor
					chunkData.setBlock(lx, FLOOR_Y, lz, Material.OBSIDIAN);
					// Perimeter wall: WALL_BASE obsidian + WALL_GLASS glass courses
					if (perimeter) {
						for (int dy = 1; dy <= WALL_BASE + WALL_GLASS; dy++) {
							chunkData.setBlock(lx, FLOOR_Y + dy, lz,
									dy <= WALL_BASE ? Material.OBSIDIAN : Material.GLASS);
						}
					}
				}
			}
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

	private List<SpawnPoint> spawnsFor(ArenaTemplate template) {
		if (template.spawnPoints() != null && template.spawnPoints().size() >= 2) {
			return template.spawnPoints();
		}
		return List.of(
				new SpawnPoint(-(RADIUS - 1), FLOOR_Y + 1.0, 0, -90f, 0f),
				new SpawnPoint( (RADIUS - 1), FLOOR_Y + 1.0, 0,  90f, 0f)
		);
	}
}
