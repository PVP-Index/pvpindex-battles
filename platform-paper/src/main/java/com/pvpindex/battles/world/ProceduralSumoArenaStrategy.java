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
 * Builds a Sumo arena: a small elevated platform surrounded by void.
 * First player knocked off the edge loses.
 *
 * <h3>Arena layout</h3>
 * <pre>
 *   - 9x9 stone-brick platform at Y=80
 *   - Chiselled stone brick border as a visual edge indicator
 *   - No walls — players fall into the void on knockback
 *   - Fall damage ON so void kills register cleanly
 * </pre>
 */
public final class ProceduralSumoArenaStrategy implements WorldGenerationStrategy {

	private static final int FLOOR_Y = 80;
	private static final int HALF = 4;

	private final Plugin plugin;

	public ProceduralSumoArenaStrategy(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String id() { return "procedural_sumo"; }

	@Override
	public ArenaInstance generate(ArenaTemplate template) throws IOException {
		UUID id = UUID.randomUUID();
		String worldName = "pvpindex_" + template.id() + "_" + id.toString().substring(0, 8);

		if (Bukkit.getServer() == null) {
			return new ArenaInstance(id, template.id(), worldName,
					spawnsFor(template), template.spectatorSpawn());
		}

		WorldCreator creator = new WorldCreator(worldName)
				.generator(new SumoArenaChunkGenerator())
				.type(WorldType.NORMAL)
				.generateStructures(false);
		World world = Bukkit.createWorld(creator);
		if (world == null) {
			throw new IOException("Failed to create sumo arena world: " + worldName);
		}

		// Arena structure is embedded in the ChunkGenerator — spawn chunks are
		// already fully built when createWorld() returns. Only game rules remain.
		configureWorld(world);

		plugin.getLogger().info("Built procedural sumo arena " + worldName + " from template " + template.id());
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
		applyRule(world, "fallDamage", true);
	}

	@SuppressWarnings("unchecked")
	private static <T> void applyRule(World world, String name, T value) {
		GameRule<T> rule = (GameRule<T>) GameRule.getByName(name);
		if (rule != null) world.setGameRule(rule, value);
	}

	// -------------------------------------------------------------------------
	// Chunk generator
	// -------------------------------------------------------------------------

	private static final class SumoArenaChunkGenerator extends ChunkGenerator {

		@Override
		public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
									int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
			int blockMinX = chunkX * 16;
			int blockMinZ = chunkZ * 16;
			int startX = Math.max(-HALF, blockMinX);
			int endX   = Math.min( HALF, blockMinX + 15);
			int startZ = Math.max(-HALF, blockMinZ);
			int endZ   = Math.min( HALF, blockMinZ + 15);
			if (startX > endX || startZ > endZ) return;

			for (int wx = startX; wx <= endX; wx++) {
				int lx = wx - blockMinX;
				for (int wz = startZ; wz <= endZ; wz++) {
					int lz = wz - blockMinZ;
					boolean border = (wx == -HALF || wx == HALF || wz == -HALF || wz == HALF);
					chunkData.setBlock(lx, FLOOR_Y, lz,
							border ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
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
				new SpawnPoint(-(HALF - 1), FLOOR_Y + 1.0, 0, -90f, 0f),
				new SpawnPoint( (HALF - 1), FLOOR_Y + 1.0, 0,  90f, 0f)
		);
	}
}
