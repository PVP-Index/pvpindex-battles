package com.pvpindex.battles.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Pastes a structure into a host world at the template's
 * {@link ArenaTemplate#pasteOrigin() origin}. Tracks the pasted region so it
 * can be wiped on {@link #release(ArenaInstance)}.
 *
 * <p>Format note: to keep the plugin dependency-free we ship a tiny JSON
 * "schematic" format (palette + RLE block stream) under
 * {@code templates/&lt;name&gt;.json}. Real {@code .schem} (Sponge) and
 * {@code .schematic} (legacy) files can be supported by dropping in a
 * <a href="https://worldedit.enginehub.org/">WorldEdit</a> reader; we
 * deliberately keep the abstraction minimal here.</p>
 */
public final class SchematicStrategy implements WorldGenerationStrategy {
    private final Plugin plugin;
    private final ObjectMapper mapper;
    private final Path templatesDir;
    private final Map<UUID, List<int[]>> pastedRegions = new HashMap<>();

    public SchematicStrategy(Plugin plugin, ObjectMapper mapper, Path templatesDir) {
        this.plugin = plugin;
        this.mapper = mapper;
        this.templatesDir = templatesDir;
    }

    @Override
    public String id() { return "schematic"; }

    @Override
    public ArenaInstance generate(ArenaTemplate template) throws IOException {
        if (Bukkit.getServer() == null) {
            // Allow tests to wire the strategy without a real server.
            return new ArenaInstance(UUID.randomUUID(), template.id(), template.hostWorld(),
                    template.spawnPoints(), template.spectatorSpawn());
        }
        World host = Bukkit.getWorld(template.hostWorld());
        if (host == null) {
            throw new IOException("Host world '" + template.hostWorld() + "' is not loaded");
        }
        // Resolve relative to templatesDir first (legacy templates.yml entries),
        // then fall back to the plugin data folder root so that schematics.yml
        // entries like "schematics/arena.schematic" resolve correctly.
        Path source = templatesDir.resolve(template.sourcePath());
        if (!Files.isRegularFile(source)) {
            source = plugin.getDataFolder().toPath().resolve(template.sourcePath());
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("Schematic file not found: " + template.sourcePath()
                    + " (checked templates/ and plugin data folder)");
        }
        JsonNode root = mapper.readTree(source.toFile());
        List<String> palette = new ArrayList<>();
        root.path("palette").forEach(n -> palette.add(n.asText()));

        UUID id = UUID.randomUUID();
        List<int[]> pasted = new ArrayList<>();
        int ox = (int) template.pasteOrigin().x();
        int oy = (int) template.pasteOrigin().y();
        int oz = (int) template.pasteOrigin().z();

        for (JsonNode entry : root.path("blocks")) {
            int dx = entry.path("x").asInt();
            int dy = entry.path("y").asInt();
            int dz = entry.path("z").asInt();
            int paletteIndex = entry.path("p").asInt();
            if (paletteIndex < 0 || paletteIndex >= palette.size()) continue;
            Material material = Material.matchMaterial(palette.get(paletteIndex));
            if (material == null) continue;
            Block block = host.getBlockAt(ox + dx, oy + dy, oz + dz);
            block.setType(material, false);
            pasted.add(new int[]{ox + dx, oy + dy, oz + dz});
        }
        pastedRegions.put(id, pasted);
        plugin.getLogger().info("Pasted schematic " + template.sourcePath() + " (" + pasted.size() + " blocks) for arena " + id);

        return new ArenaInstance(id, template.id(), host.getName(),
                template.spawnPoints(), template.spectatorSpawn());
    }

    @Override
    public void release(ArenaInstance instance) {
        if (Bukkit.getServer() == null) return;
        World host = Bukkit.getWorld(instance.worldName());
        if (host == null) return;
        List<int[]> region = pastedRegions.remove(instance.instanceId());
        if (region == null) return;
        for (int[] xyz : region) {
            host.getBlockAt(new Location(host, xyz[0], xyz[1], xyz[2])).setType(Material.AIR, false);
        }
    }
}
