package com.pvpindex.battles.battle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

/**
 * Saves a {@link PlayerStateSnapshot} for every player who enters a battle
 * and restores it when the battle ends or when the player rejoins after a
 * mid-battle disconnect/crash.
 *
 * <p>Snapshots are mirrored to {@code plugins/PvPIndexBattles/state/&lt;uuid&gt;.yml}
 * so that a server crash mid-battle still leaves a path back to the
 * player's original inventory and location.</p>
 */
public final class PlayerStateService {

    private final Plugin plugin;
    private final Path stateDir;
    private final boolean includeEnderChest;
    private final com.pvpindex.battles.version.VersionAdapter versionAdapter;
    private final Map<UUID, PlayerStateSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile Location afterBattleLocation;

    public PlayerStateService(Plugin plugin, boolean includeEnderChest, com.pvpindex.battles.version.VersionAdapter versionAdapter) {
        this.plugin = plugin;
        this.includeEnderChest = includeEnderChest;
        this.versionAdapter = versionAdapter;
        this.stateDir = plugin.getDataFolder().toPath().resolve("state");
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create state dir: " + e.getMessage());
        }
    }

    /**
     * Sets the fixed after-battle location. When non-null, all restores
     * teleport to this location instead of the snapshot's original position.
     */
    public void setAfterBattleLocation(Location location) {
        this.afterBattleLocation = location;
    }

    public boolean hasSnapshot(UUID playerUuid) {
        return snapshots.containsKey(playerUuid) || Files.exists(stateFile(playerUuid));
    }

    /** Capture and persist the player's current state. No-op if one already exists. */
    public void save(Player player) {
        UUID id = player.getUniqueId();
        if (snapshots.containsKey(id)) return; // already saved for an earlier battle
        PlayerStateSnapshot snap = PlayerStateSnapshot.capture(player, includeEnderChest, versionAdapter.getMaxHealthAttribute());
        snapshots.put(id, snap);
        try {
            persist(id, snap);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to persist player state for " + id + ": " + e.getMessage());
        }
    }

    /**
     * Restore the player's state. Tries the in-memory snapshot first, then
     * falls back to the on-disk copy (used after a crash). Removes the
     * snapshot once successfully applied.
     */
    public boolean restore(Player player) {
        UUID id = player.getUniqueId();
        PlayerStateSnapshot snap = snapshots.remove(id);
        if (snap == null) {
            try {
                snap = readPersisted(id);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read persisted state for " + id + ": " + e.getMessage());
            }
        }
        if (snap == null) return false;
        snap.restore(player, versionAdapter.getMaxHealthAttribute(), afterBattleLocation);
        discard(id);
        return true;
    }

    /**
     * Restore everything <em>except</em> inventory (armor, offhand, ender chest).
     * The player keeps whatever items they currently have. Used by SMP battles
     * so the winner retains looted items. Consumes the snapshot like
     * {@link #restore}.
     */
    public boolean restoreWithoutInventory(Player player) {
        UUID id = player.getUniqueId();
        PlayerStateSnapshot snap = snapshots.remove(id);
        if (snap == null) {
            try {
                snap = readPersisted(id);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read persisted state for " + id + ": " + e.getMessage());
            }
        }
        if (snap == null) return false;
        snap.restoreWithoutInventory(player, versionAdapter.getMaxHealthAttribute(), afterBattleLocation);
        discard(id);
        return true;
    }

    /** Drop both in-memory and on-disk snapshot without restoring. */
    public void discard(UUID playerUuid) {
        snapshots.remove(playerUuid);
        try {
            Files.deleteIfExists(stateFile(playerUuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete state file for " + playerUuid + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Persistence (YAML + Bukkit's ItemStack serialization)
    // -------------------------------------------------------------------------

    private Path stateFile(UUID playerUuid) {
        return stateDir.resolve(playerUuid + ".yml");
    }

    private void persist(UUID id, PlayerStateSnapshot snap) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        Location loc = snap.location();
        cfg.set("location.world", loc.getWorld() == null ? null : loc.getWorld().getName());
        cfg.set("location.x", loc.getX());
        cfg.set("location.y", loc.getY());
        cfg.set("location.z", loc.getZ());
        cfg.set("location.yaw", loc.getYaw());
        cfg.set("location.pitch", loc.getPitch());
        cfg.set("game_mode", snap.gameMode().name());
        cfg.set("health", snap.health());
        cfg.set("max_health", snap.maxHealth());
        cfg.set("food_level", snap.foodLevel());
        cfg.set("saturation", snap.saturation());
        cfg.set("exhaustion", snap.exhaustion());
        cfg.set("exp", snap.exp());
        cfg.set("level", snap.level());
        cfg.set("fire_ticks", snap.fireTicks());
        cfg.set("fall_distance", snap.fallDistance());
        cfg.set("allow_flight", snap.allowFlight());
        cfg.set("is_flying", snap.isFlying());
        cfg.set("inventory", encodeItems(snap.inventory()));
        cfg.set("armor", encodeItems(snap.armor()));
        cfg.set("offhand", encodeItem(snap.offhand()));
        if (snap.enderChest() != null) {
            cfg.set("ender_chest", encodeItems(snap.enderChest()));
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect e : snap.potionEffects()) {
            effects.add(e.serialize());
        }
        cfg.set("potion_effects", effects);
        cfg.save(stateFile(id).toFile());
    }

    @SuppressWarnings("unchecked")
    private PlayerStateSnapshot readPersisted(UUID id) throws IOException {
        Path file = stateFile(id);
        if (!Files.exists(file)) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());

        World world = cfg.isString("location.world") ? Bukkit.getWorld(cfg.getString("location.world")) : null;
        if (world == null) {
            // World might not be loaded; refuse to restore until it is.
            plugin.getLogger().warning("Cannot restore " + id + ": world '"
                    + cfg.getString("location.world") + "' not loaded.");
            return null;
        }
        Location loc = new Location(world,
                cfg.getDouble("location.x"), cfg.getDouble("location.y"), cfg.getDouble("location.z"),
                (float) cfg.getDouble("location.yaw"), (float) cfg.getDouble("location.pitch"));

        ItemStack[] inv = decodeItems(cfg.getStringList("inventory"));
        ItemStack[] armor = decodeItems(cfg.getStringList("armor"));
        ItemStack offhand = decodeItem(cfg.getString("offhand"));
        ItemStack[] ender = cfg.contains("ender_chest") ? decodeItems(cfg.getStringList("ender_chest")) : null;

        List<PotionEffect> effects = new ArrayList<>();
        for (Object raw : cfg.getList("potion_effects", List.of())) {
            if (raw instanceof Map<?, ?> map) {
                effects.add(new PotionEffect((Map<String, Object>) map));
            }
        }

        return new PlayerStateSnapshot(
                loc,
                GameMode.valueOf(cfg.getString("game_mode", "SURVIVAL")),
                cfg.getDouble("health", 20.0),
                cfg.getDouble("max_health", 20.0),
                cfg.getInt("food_level", 20),
                (float) cfg.getDouble("saturation", 5.0),
                (float) cfg.getDouble("exhaustion", 0.0),
                (float) cfg.getDouble("exp", 0.0),
                cfg.getInt("level", 0),
                cfg.getInt("fire_ticks", 0),
                (float) cfg.getDouble("fall_distance", 0.0),
                cfg.getBoolean("allow_flight", false),
                cfg.getBoolean("is_flying", false),
                inv, armor, offhand, ender, effects
        );
    }

    private static List<String> encodeItems(ItemStack[] items) {
        List<String> out = new ArrayList<>();
        for (ItemStack it : items) {
            out.add(encodeItem(it));
        }
        return out;
    }

    private static String encodeItem(ItemStack item) {
        // Paper rejects serializing AIR / empty stacks ("Empty itemstack cannot
        // be serialized"). Treat them the same as null so empty inventory slots
        // round-trip cleanly.
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return "";
        byte[] bytes = item.serializeAsBytes();
        return java.util.Base64.getEncoder().encodeToString(bytes);
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
        byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
        return ItemStack.deserializeBytes(bytes);
    }

    // Suppress unused-warnings for streams kept for future binary fallback.
    @SuppressWarnings("unused")
    private static void writeBinary(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
    }

    @SuppressWarnings("unused")
    private static byte[] readBinary(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] buf = new byte[len];
        in.readFully(buf);
        return buf;
    }
}
