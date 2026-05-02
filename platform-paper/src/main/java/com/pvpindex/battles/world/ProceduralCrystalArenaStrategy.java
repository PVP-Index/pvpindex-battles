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
				.generator(new VoidWorldGenerator())
				.type(WorldType.NORMAL)
				.generateStructures(false);
		World world = Bukkit.createWorld(creator);
		if (world == null) {
			throw new IOException("Failed to create crystal arena world: " + worldName);
		}

		configureWorld(world);
		buildArena(world);

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
	}

	private void buildArena(World world) {
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				world.getBlockAt(x, FLOOR_Y, z).setType(Material.OBSIDIAN, false);
			}
		}
		for (int x = -RADIUS; x <= RADIUS; x++) {
			placeWall(world, x, -RADIUS);
			placeWall(world, x,  RADIUS);
		}
		for (int z = -RADIUS + 1; z <= RADIUS - 1; z++) {
			placeWall(world, -RADIUS, z);
			placeWall(world,  RADIUS, z);
		}
	}

	private void placeWall(World world, int x, int z) {
		for (int dy = 1; dy <= WALL_BASE + WALL_GLASS; dy++) {
			Material mat = (dy <= WALL_BASE) ? Material.OBSIDIAN : Material.GLASS;
			world.getBlockAt(x, FLOOR_Y + dy, z).setType(mat, false);
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
