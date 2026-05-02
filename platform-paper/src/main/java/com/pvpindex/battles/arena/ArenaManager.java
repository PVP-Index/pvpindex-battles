package com.pvpindex.battles.arena;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ArenaManager {
    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        arenas.clear();
        File file = new File(plugin.getDataFolder(), "arenas.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<SpawnPoint> spawns = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("spawn_points")) {
                spawns.add(new SpawnPoint(
                        asDouble(raw.get("x")),
                        asDouble(raw.get("y")),
                        asDouble(raw.get("z")),
                        (float) asDouble(raw.containsKey("yaw") ? raw.get("yaw") : 0.0D),
                        (float) asDouble(raw.containsKey("pitch") ? raw.get("pitch") : 0.0D)
                ));
            }
            SpawnPoint spectator = null;
            ConfigurationSection spec = section.getConfigurationSection("spectator_spawn");
            if (spec != null) {
                spectator = new SpawnPoint(spec.getDouble("x"), spec.getDouble("y"), spec.getDouble("z"), (float) spec.getDouble("yaw"), (float) spec.getDouble("pitch"));
            }
            arenas.put(id, new Arena(id, section.getString("name", id), section.getString("world", "world"), spawns, spectator, section.getBoolean("enabled", true)));
        }
    }

    private double asDouble(Object obj) {
        return obj instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(obj));
    }

    public Optional<Arena> find(String id) {
        return Optional.ofNullable(arenas.get(id));
    }
}
