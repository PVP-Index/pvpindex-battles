package com.pvpindex.battles.world;

import com.pvpindex.battles.arena.SpawnPoint;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Front door for arena generation. Owns the strategy registry, loads
 * {@link ArenaTemplate}s from {@code templates.yml} and
 * {@link SchematicLoader} (schematics.yml), and is the only thing
 * {@code BattleService} talks to.
 */
public final class WorldGeneratorService {
    private final JavaPlugin plugin;
    private SchematicLoader schematicLoader;
    private final Map<String, WorldGenerationStrategy> strategies = new LinkedHashMap<>();
    private final Map<String, ArenaTemplate> templates = new LinkedHashMap<>();
    private final Map<String, ArenaInstance> activeInstances = new HashMap<>();

    public WorldGeneratorService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attaches a {@link SchematicLoader} whose templates are merged into
     * {@link #templates} on every {@link #reload(Path)} call.
     */
    public void setSchematicLoader(SchematicLoader schematicLoader) {
        this.schematicLoader = schematicLoader;
    }

    public void register(WorldGenerationStrategy strategy) {
        strategies.put(strategy.id(), strategy);
    }

    public void reload(Path templatesDir) {
        templates.clear();
        File file = new File(plugin.getDataFolder(), "templates.yml");
        if (!file.exists()) {
            plugin.saveResource("templates.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("templates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            templates.put(id, new ArenaTemplate(
                    id,
                    s.getString("display_name", id),
                    s.getString("strategy", "copy"),
                    s.getString("source_path", id),
                    s.getString("host_world", "world"),
                    parseSpawns(s.getMapList("spawn_points")),
                    parseSpawn(s.getConfigurationSection("spectator_spawn")),
                    parseSpawn(s.getConfigurationSection("paste_origin"))
            ));
        }
        plugin.getLogger().info("Loaded " + templates.size() + " arena template(s) from templates.yml");

        // Merge schematic-based templates from schematics.yml (may override
        // templates.yml entries with the same id. schematics.yml wins).
        if (schematicLoader != null) {
            schematicLoader.reload();
            templates.putAll(schematicLoader.templates());
            plugin.getLogger().info("Total arena templates after schematic merge: " + templates.size());
        }
    }

    public Optional<ArenaInstance> generate(String templateId) throws Exception {
        ArenaTemplate template = templates.get(templateId);
        if (template == null) return Optional.empty();
        WorldGenerationStrategy strategy = strategies.get(template.strategy());
        if (strategy == null) {
            throw new IllegalStateException("No world generation strategy registered for '" + template.strategy() + "'");
        }
        ArenaInstance instance = strategy.generate(template);
        activeInstances.put(instance.instanceId().toString(), instance);
        return Optional.of(instance);
    }

    public void release(ArenaInstance instance) throws Exception {
        ArenaTemplate template = templates.get(instance.templateId());
        if (template == null) return;
        WorldGenerationStrategy strategy = strategies.get(template.strategy());
        if (strategy != null) {
            strategy.release(instance);
        }
        activeInstances.remove(instance.instanceId().toString());
    }

    public Optional<ArenaTemplate> findTemplate(String id) { return Optional.ofNullable(templates.get(id)); }

    /** All known template ids (read-only snapshot). */
    public java.util.Set<String> templateIds() {
        return java.util.Set.copyOf(templates.keySet());
    }

    private static List<SpawnPoint> parseSpawns(List<Map<?, ?>> raw) {
        List<SpawnPoint> out = new java.util.ArrayList<>();
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
        return new SpawnPoint(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0d;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ex) { return 0.0d; }
    }
}
