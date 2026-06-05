package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads schematic spawn-point metadata from {@code schematics.yml} and
 * produces ready-to-use {@link ArenaTemplate} objects with
 * {@code strategy: schematic}.
 *
 * <p>Server owners manage spawn locations by editing
 * {@code plugins/PvPIndexBattles/schematics.yml}. Each entry maps a
 * schematic file in the {@code schematics/} sub-folder to its
 * {@code paste_origin}, player {@code spawn_points}, and
 * {@code spectator_spawn}. all without touching code.</p>
 *
 * <p>Call {@link #reload()} on startup and whenever {@code /pvpindex reload}
 * is executed. The resulting templates are merged into
 * {@link WorldGeneratorService} via {@link #templates()}.</p>
 */
public final class SchematicLoader {

    private final JavaPlugin plugin;
    private final Map<String, ArenaTemplate> loaded = new LinkedHashMap<>();

    public SchematicLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads {@code schematics.yml} from the plugin data folder, creating it
     * from the bundled default if it does not yet exist. Safe to call
     * repeatedly (reloads from disk each time).
     */
    public void reload() {
        loaded.clear();

        File file = new File(plugin.getDataFolder(), "schematics.yml");
        if (!file.exists()) {
            plugin.saveResource("schematics.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("schematics");
        if (root == null) {
            plugin.getLogger().warning("schematics.yml has no 'schematics:' section. no schematic arenas loaded");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String schematicFile = s.getString("file", id + ".json");
            // source_path is relative to the plugin data folder root so that
            // SchematicStrategy can resolve it without knowing this sub-folder.
            String sourcePath = "schematics/" + schematicFile;

            loaded.put(id, new ArenaTemplate(
                    id,
                    s.getString("display_name", id),
                    "schematic",
                    sourcePath,
                    s.getString("host_world", "world"),
                    parseSpawns(s.getMapList("spawn_points")),
                    parseSpawn(s.getConfigurationSection("spectator_spawn")),
                    parseSpawn(s.getConfigurationSection("paste_origin"))
            ));
        }

        plugin.getLogger().info("Loaded " + loaded.size() + " schematic arena(s) from schematics.yml");
    }

    /**
     * Returns an immutable snapshot of the templates loaded by the most
     * recent {@link #reload()} call, keyed by template id.
     */
    public Map<String, ArenaTemplate> templates() {
        return Map.copyOf(loaded);
    }

    // -------------------------------------------------------------------------
    // Parsing helpers (mirrors WorldGeneratorService to stay self-contained)
    // -------------------------------------------------------------------------

    private static List<SpawnPoint> parseSpawns(List<Map<?, ?>> raw) {
        List<SpawnPoint> out = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            out.add(new SpawnPoint(
                    asDouble(entry.get("x")),
                    asDouble(entry.get("y")),
                    asDouble(entry.get("z")),
                    (float) asDouble(entry.get("yaw")),
                    (float) asDouble(entry.get("pitch"))
            ));
        }
        return out;
    }

    private static SpawnPoint parseSpawn(ConfigurationSection s) {
        if (s == null) return null;
        return new SpawnPoint(
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch")
        );
    }

    private static double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0d;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }
}
