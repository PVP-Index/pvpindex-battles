package com.pvpindex.battles.world;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

/**
 * Duplicates a pristine template world directory into a fresh per-battle
 * world. Inspired by the "world template" pattern used by Hypixel and most
 * mini-game networks.
 */
public final class WorldCopyStrategy implements WorldGenerationStrategy {
    private final Plugin plugin;
    private final Path templatesDir;
    private final Path runtimeDir;

    public WorldCopyStrategy(Plugin plugin, Path templatesDir, Path runtimeDir) {
        this.plugin = plugin;
        this.templatesDir = templatesDir;
        this.runtimeDir = runtimeDir;
    }

    @Override
    public String id() { return "copy"; }

    @Override
    public ArenaInstance generate(ArenaTemplate template) throws IOException {
        Path source = templatesDir.resolve(template.sourcePath());
        if (!Files.isDirectory(source)) {
            throw new IOException("Template world directory not found: " + source);
        }
        UUID id = UUID.randomUUID();
        String worldName = "pvpindex_" + template.id() + "_" + id.toString().substring(0, 8);
        Path dest = runtimeDir.resolve(worldName);
        copyDirectory(source, dest);
        // Bukkit needs the world inside the server folder; symlink/copy lives there if runtimeDir == server root.
        WorldCreator creator = new WorldCreator(worldName);
        World world = (Bukkit.getServer() != null) ? Bukkit.createWorld(creator) : null;
        // Ensure PvP is always enabled in the arena world, regardless of the
        // template world's setting or server-wide defaults.
        if (world != null) {
            world.setPVP(true);
        }
        plugin.getLogger().info("Generated arena world " + worldName + " from template " + template.id());
        return new ArenaInstance(
                id,
                template.id(),
                world == null ? worldName : world.getName(),
                template.spawnPoints(),
                template.spectatorSpawn()
        );
    }

    @Override
    public void release(ArenaInstance instance) throws IOException {
        if (Bukkit.getServer() != null) {
            World world = Bukkit.getWorld(instance.worldName());
            if (world != null) {
                Bukkit.unloadWorld(world, false);
            }
        }
        Path dir = runtimeDir.resolve(instance.worldName());
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    private static void copyDirectory(Path source, Path dest) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path target = dest.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
