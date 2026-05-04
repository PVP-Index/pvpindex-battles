package com.pvpindex.battles.gamemode;

import com.pvpindex.battles.battle.type.GameModeType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YAML loader for {@link GameModeDefinition} / {@link KitDefinition}. The
 * file shipped at {@code src/main/resources/gamemodes.yml} acts as both the
 * default and a worked example.
 */
public final class GameModeLoader {
    private final JavaPlugin plugin;
    private final GameModeRegistry registry;

    public GameModeLoader(JavaPlugin plugin, GameModeRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gamemodes.yml");
        if (!file.exists()) {
            plugin.saveResource("gamemodes.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        List<KitDefinition> kits = new ArrayList<>();
        ConfigurationSection kitSection = cfg.getConfigurationSection("kits");
        if (kitSection != null) {
            for (String kitId : kitSection.getKeys(false)) {
                ConfigurationSection k = kitSection.getConfigurationSection(kitId);
                if (k == null) continue;
                List<KitItem> items = new ArrayList<>();
                for (Map<?, ?> raw : k.getMapList("items")) {
                    items.add(new KitItem(
                            String.valueOf(raw.get("slot") == null ? "0" : raw.get("slot")),
                            String.valueOf(raw.get("material") == null ? "AIR" : raw.get("material")),
                            asInt(raw.get("amount"), 1),
                            asEnchantMap(raw.get("enchantments")),
                            asStringList(raw.get("lore")),
                            raw.get("display_name") == null ? null : String.valueOf(raw.get("display_name"))
                    ));
                }
                kits.add(new KitDefinition(kitId, k.getString("display_name", kitId), items, k.getStringList("potion_effects")));
            }
        }

        List<GameModeDefinition> modes = new ArrayList<>();
        ConfigurationSection modeSection = cfg.getConfigurationSection("modes");
        if (modeSection != null) {
            for (String id : modeSection.getKeys(false)) {
                ConfigurationSection m = modeSection.getConfigurationSection(id);
                if (m == null) continue;
                ConfigurationSection r = m.getConfigurationSection("rules");
                GameModeRules rules = r == null ? GameModeRules.vanilla() : new GameModeRules(
                        r.getInt("time_limit_seconds", 0),
                        r.getInt("countdown_seconds", 5),
                        r.getBoolean("allow_block_break", true),
                        r.getBoolean("allow_block_place", true),
                        r.getBoolean("natural_regeneration", true),
                        r.getBoolean("keep_inventory_on_death", false),
                        r.getBoolean("allow_item_drops", true),
                        r.getBoolean("allow_food_loss", true),
                        r.getBoolean("friendly_fire", false),
                        r.getInt("max_participants", 2),
                        r.getInt("teams_count", 2),
                        r.getDouble("start_health", 20.0d),
                        r.getInt("start_food_level", 20),
                        r.getBoolean("use_player_inventory", false),
                        r.getInt("loot_cooldown_seconds", 0)
                );
                GameModeType legacy = parseLegacy(m.getString("legacy_type", "VANILLA"));
                modes.add(new GameModeDefinition(
                        id,
                        m.getString("display_name", id),
                        m.getStringList("description"),
                        legacy,
                        rules,
                        m.getString("kit"),
                        m.getString("world.strategy", "none"),
                        m.getString("world.template", null),
                        m.getStringList("arenas"),
                        m.getStringList("win_conditions")
                ));
            }
        }

        registry.replaceAll(modes, kits);
    }

    private GameModeType parseLegacy(String value) {
        try {
            return GameModeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown legacy_type '" + value + "', defaulting to VANILLA");
            return GameModeType.VANILLA;
        }
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> asEnchantMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), asInt(entry.getValue(), 1));
            }
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return List.of();
    }
}
