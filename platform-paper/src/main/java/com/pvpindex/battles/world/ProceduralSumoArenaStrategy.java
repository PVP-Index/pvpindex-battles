package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

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
				.generator(new VoidWorldGenerator())
				.type(WorldType.NORMAL)
				.generateStructures(false);
		World world = Bukkit.createWorld(creator);
		if (world == null) {
			throw new IOException("Failed to create sumo arena world: " + worldName);
		}

		configureWorld(world);
		buildArena(world);

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
		world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
		world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
		world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
		world.setGameRule(GameRule.DO_FIRE_TICK, false);
		world.setGameRule(GameRule.MOB_GRIEFING, false);
		world.setGameRule(GameRule.KEEP_INVENTORY, false);
		world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
		world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
		world.setTime(6000L);
		world.setStorm(false);
		world.setThundering(false);
		world.setGameRule(GameRule.FALL_DAMAGE, true);
	}

	private void buildArena(World world) {
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				Block b = world.getBlockAt(x, FLOOR_Y, z);
				boolean isBorder = (x == -HALF || x == HALF || z == -HALF || z == HALF);
				b.setType(isBorder ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS, false);
			}
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
