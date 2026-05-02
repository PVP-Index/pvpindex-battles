package com.pvpindex.battles.battle;

import com.pvpindex.battles.version.VersionAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

/**
 * Captures, persists, and serialises {@link BattleStartSetup} records.
 *
 * <p>When a battle starts, a setup is captured for every online participant and
 * immediately written to {@code plugins/PvPIndexBattles/setup/<battle-uuid>.yml}
 * for crash-safety. The file is removed once the battle payload has been
 * submitted to the API.</p>
 *
 * <p>Uses the same Base64 + {@code ItemStack.serializeAsBytes()} encoding as
 * {@link PlayerStateService} so items survive server restarts reliably.</p>
 */
public final class BattleStartSetupService {

    private final Plugin plugin;
    private final Path setupDir;
    private final Attribute maxHealthAttr;

    public BattleStartSetupService(Plugin plugin, VersionAdapter versionAdapter) {
        this.plugin = plugin;
        this.maxHealthAttr = versionAdapter.getMaxHealthAttribute();
        this.setupDir = plugin.getDataFolder().toPath().resolve("setup");
        try {
            Files.createDirectories(setupDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[BattleStartSetupService] Could not create setup dir: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Capture
    // -------------------------------------------------------------------------

    /** Snapshot the player's current state (call after kit application). */
    public BattleStartSetup capture(Player player) {
        return BattleStartSetup.capture(player, maxHealthAttr);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Write all per-player setups for a battle to disk. Safe to call with an
     * empty or null map (no-op).
     */
    public void persist(UUID battleUuid, Map<UUID, BattleStartSetup> setups) {
        if (setups == null || setups.isEmpty()) return;
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("battle_uuid", battleUuid.toString());
        Map<String, Object> participants = new LinkedHashMap<>();
        for (Map.Entry<UUID, BattleStartSetup> entry : setups.entrySet()) {
            participants.put(entry.getKey().toString(), serialiseSetup(entry.getValue()));
        }
        cfg.set("participants", participants);
        try {
            cfg.save(setupFile(battleUuid).toFile());
        } catch (IOException e) {
            plugin.getLogger().warning("[BattleStartSetupService] Failed to persist setups for battle "
                    + battleUuid + ": " + e.getMessage());
        }
    }

    /**
     * Load setups from disk (used for crash recovery when the in-memory map has
     * been lost). Returns an empty map if the file does not exist.
     */
    @SuppressWarnings("unchecked")
    public Map<UUID, BattleStartSetup> load(UUID battleUuid) {
        Path file = setupFile(battleUuid);
        if (!Files.exists(file)) return Map.of();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());
        Object raw = cfg.get("participants");
        if (!(raw instanceof Map<?, ?> rawMap)) return Map.of();
        Map<UUID, BattleStartSetup> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            try {
                UUID playerUuid = UUID.fromString(entry.getKey().toString());
                if (!(entry.getValue() instanceof Map<?, ?> playerMap)) continue;
                result.put(playerUuid, deserialiseSetup(playerUuid, (Map<String, Object>) playerMap));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[BattleStartSetupService] Invalid UUID in setup file for battle "
                        + battleUuid + ": " + entry.getKey());
            }
        }
        return result;
    }

    /** Delete the on-disk setup file once the battle has been submitted. */
    public void cleanup(UUID battleUuid) {
        try {
            Files.deleteIfExists(setupFile(battleUuid));
        } catch (IOException e) {
            plugin.getLogger().warning("[BattleStartSetupService] Could not delete setup file for battle "
                    + battleUuid + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // API serialisation
    // -------------------------------------------------------------------------

    /**
     * Convert a {@link BattleStartSetup} into a plain {@code Map<String, Object>}
     * for embedding in the API battle payload.
     *
     * <p>Items are represented as human-readable maps {@code {type, amount,
     * enchantments?}} so the web frontend can display them without any binary
     * decoding. Empty/air slots are represented as {@code null} list elements.
     * Crash-recovery YAML persistence still uses Base64 bytes (see
     * {@link #serialiseSetup}).</p>
     */
    public Map<String, Object> toApiMap(BattleStartSetup setup) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("captured_at", setup.capturedAt().toString());
        map.put("health", setup.health());
        map.put("max_health", setup.maxHealth());
        map.put("food_level", setup.foodLevel());
        map.put("hotbar", itemsToApiList(setup.hotbar()));
        map.put("inventory", itemsToApiList(setup.mainInventory()));
        map.put("armor", itemsToApiList(setup.armor()));
        map.put("offhand", itemToApiMap(setup.offhand()));
        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect e : setup.potionEffects()) {
            effects.add(e.serialize());
        }
        map.put("potion_effects", effects);
        return map;
    }

    /** Serialise one {@link ItemStack} as a readable map, or {@code null} for empty slots. */
    private static Map<String, Object> itemToApiMap(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", item.getType().name());
        out.put("amount", item.getAmount());
        Map<String, Integer> enchants = new LinkedHashMap<>();
        item.getEnchantments().forEach((ench, lvl) ->
                enchants.put(ench.getKey().getKey(), lvl));
        if (!enchants.isEmpty()) out.put("enchantments", enchants);
        return out;
    }

    /** Map an array of {@link ItemStack} to a list of readable maps (nulls for empty slots). */
    private static List<Object> itemsToApiList(ItemStack[] items) {
        List<Object> out = new ArrayList<>();
        if (items == null) return out;
        for (ItemStack item : items) {
            out.add(itemToApiMap(item));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Path setupFile(UUID battleUuid) {
        return setupDir.resolve(battleUuid + ".yml");
    }

    private static Map<String, Object> serialiseSetup(BattleStartSetup s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("captured_at", s.capturedAt().toString());
        map.put("health", s.health());
        map.put("max_health", s.maxHealth());
        map.put("food_level", s.foodLevel());
        map.put("hotbar", encodeItems(s.hotbar()));
        map.put("inventory", encodeItems(s.mainInventory()));
        map.put("armor", encodeItems(s.armor()));
        map.put("offhand", encodeItem(s.offhand()));
        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect e : s.potionEffects()) {
            effects.add(e.serialize());
        }
        map.put("potion_effects", effects);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static BattleStartSetup deserialiseSetup(UUID playerUuid, Map<String, Object> map) {
        Instant capturedAt = map.containsKey("captured_at")
                ? Instant.parse((String) map.get("captured_at"))
                : Instant.EPOCH;
        double health    = toDouble(map.get("health"), 20.0);
        double maxHealth = toDouble(map.get("max_health"), 20.0);
        int foodLevel    = toInt(map.get("food_level"), 20);

        ItemStack[] hotbar    = decodeItems((List<String>) map.getOrDefault("hotbar",    List.of()));
        ItemStack[] inventory = decodeItems((List<String>) map.getOrDefault("inventory", List.of()));
        ItemStack[] armor     = decodeItems((List<String>) map.getOrDefault("armor",     List.of()));
        ItemStack offhand     = decodeItem((String)        map.getOrDefault("offhand", ""));

        List<PotionEffect> effects = new ArrayList<>();
        Object rawEffects = map.get("potion_effects");
        if (rawEffects instanceof List<?> effectList) {
            for (Object raw : effectList) {
                if (raw instanceof Map<?, ?> em) {
                    effects.add(new PotionEffect((Map<String, Object>) em));
                }
            }
        }

        return new BattleStartSetup(
                playerUuid, health, maxHealth, foodLevel,
                hotbar, inventory, armor, offhand,
                List.copyOf(effects), capturedAt);
    }

    private static List<String> encodeItems(ItemStack[] items) {
        List<String> out = new ArrayList<>();
        if (items == null) return out;
        for (ItemStack item : items) {
            out.add(encodeItem(item));
        }
        return out;
    }

    private static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return "";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack[] decodeItems(List<String> raw) {
        ItemStack[] out = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            out[i] = decodeItem(raw.get(i));
        }
        return out;
    }

    private static ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    }

    private static double toDouble(Object v, double def) {
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static int toInt(Object v, int def) {
        return v instanceof Number n ? n.intValue() : def;
    }
}
