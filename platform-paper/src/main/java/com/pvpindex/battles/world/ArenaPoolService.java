package com.pvpindex.battles.world;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Maintains a small pool of pre-generated arena instances per template so
 * matchmaking can teleport players immediately when a game is found.
 *
 * <p>Pool behaviour:</p>
 * <ul>
 *   <li>{@link #warmAll(int)} fills each template's pool up to {@code warmSize}
 *       asynchronously on plugin start.</li>
 *   <li>{@link #acquire(String)} pops a warm instance, or falls back to a
 *       synchronous generate when the pool is empty (and triggers an async
 *       refill).</li>
 *   <li>{@link #release(ArenaInstance)} either resets the instance and
 *       returns it to the pool (schematic strategy) or destroys it and
 *       async-generates a fresh replacement (copy strategy).</li>
 *   <li>{@link #shutdown()} unloads + deletes every tracked world and
 *       sweeps the world container for orphan {@code pvpindex_*} folders
 *       left behind by previous crashes.</li>
 * </ul>
 */
public final class ArenaPoolService {

    /** Prefix every arena world name uses — see {@link WorldCopyStrategy}. */
    public static final String WORLD_PREFIX = "pvpindex_";

    private final JavaPlugin plugin;
    private final WorldGeneratorService generator;
    private final Map<String, Deque<ArenaInstance>> pool = new ConcurrentHashMap<>();
    /** All instance world names ever produced by the pool — used at shutdown. */
    private final Set<String> trackedWorlds = ConcurrentHashMap.newKeySet();
    /** Templates whose first warm attempt failed — stop trying to refill. */
    private final Set<String> brokenTemplates = ConcurrentHashMap.newKeySet();
    private int warmSize = 2;
    private boolean refillAsync = true;

    public ArenaPoolService(JavaPlugin plugin, WorldGeneratorService generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    public void configure(int warmSize, boolean refillAsync) {
        this.warmSize = Math.max(0, warmSize);
        this.refillAsync = refillAsync;
    }

    /** Prefill {@code warmSize} instances per known template, asynchronously. */
    public void warmAll(int warmSize) {
        configure(warmSize, this.refillAsync);
        for (String templateId : generator.templateIds()) {
            for (int i = 0; i < warmSize; i++) {
                generateAsync(templateId);
            }
        }
    }

    /**
     * Try to pop a warm arena. If none is warm we fall back to synchronous
     * generation so the caller still gets an arena (slower path) and we
     * trigger an async refill.
     */
    public Optional<ArenaInstance> acquire(String templateId) {
        Deque<ArenaInstance> q = pool.get(templateId);
        ArenaInstance instance = q == null ? null : q.pollFirst();

        if (instance == null) {
            try {
                instance = generator.generate(templateId).orElse(null);
                if (instance != null) {
                    trackedWorlds.add(instance.worldName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ArenaPool] Sync generate failed for '"
                        + templateId + "': " + e.getMessage());
            }
            // refill asynchronously to keep the pool warm
            generateAsync(templateId);
        }
        return Optional.ofNullable(instance);
    }

    /**
     * Reclaim an arena. Copy-strategy worlds are dirty after a battle and
     * are unloaded + deleted; we then async-generate a fresh replacement.
     * Schematic-strategy instances are reset (clear paste region) and
     * returned to the warm pool.
     */
    public void release(ArenaInstance instance) {
        if (instance == null) return;
        Optional<ArenaTemplate> tpl = generator.findTemplate(instance.templateId());
        boolean isSchematic = tpl.map(t -> "schematic".equalsIgnoreCase(t.strategy())).orElse(false);
        boolean isProcedural = tpl.map(t -> "procedural".equalsIgnoreCase(t.strategy())).orElse(false);

        if (isSchematic) {
            try {
                generator.release(instance);                       // clears paste region
                ArenaInstance refreshed = generator.generate(instance.templateId()).orElse(null);
                if (refreshed != null) {
                    pool.computeIfAbsent(instance.templateId(), k -> new ArrayDeque<>()).addLast(refreshed);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ArenaPool] Schematic reset failed for '"
                        + instance.templateId() + "': " + e.getMessage());
            }
        } else {
            // Copy + procedural strategies both produce dedicated pvpindex_*
            // worlds — the cleanest reset is to unload+delete and warm a
            // fresh one. unloadAndDelete handles procedural worlds since the
            // strategy's release() is a no-op.
            try {
                if (isProcedural) {
                    unloadAndDelete(instance.worldName());
                } else {
                    generator.release(instance);                   // unload + delete world folder
                }
                trackedWorlds.remove(instance.worldName());
            } catch (Exception e) {
                plugin.getLogger().warning("[ArenaPool] Failed to release world '"
                        + instance.worldName() + "': " + e.getMessage());
            }
            generateAsync(instance.templateId());
        }
    }

    private void generateAsync(String templateId) {
        if (brokenTemplates.contains(templateId)) return;
        Runnable task = () -> {
            try {
                ArenaInstance fresh = generator.generate(templateId).orElse(null);
                if (fresh == null) {
                    if (brokenTemplates.add(templateId)) {
                        plugin.getLogger().warning("[ArenaPool] Template '" + templateId
                                + "' is unknown — skipping further warm attempts.");
                    }
                    return;
                }
                trackedWorlds.add(fresh.worldName());
                pool.computeIfAbsent(templateId, k -> new ArrayDeque<>()).addLast(fresh);
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[ArenaPool] Warmed arena '" + templateId
                            + "' → " + fresh.worldName() + " (pool=" + poolSize(templateId) + ")");
                }
            } catch (Exception e) {
                if (brokenTemplates.add(templateId)) {
                    plugin.getLogger().warning("[ArenaPool] Disabling warm refill for '"
                            + templateId + "' after first failure: " + e.getMessage());
                }
            }
        };
        if (refillAsync && Bukkit.getServer() != null) {
            // Anything that mutates blocks or calls Bukkit.createWorld must
            // run on the main thread; only the pure-IO copy strategy is safe
            // to warm asynchronously.
            Optional<ArenaTemplate> tpl = generator.findTemplate(templateId);
            boolean isCopy = tpl.map(t -> "copy".equalsIgnoreCase(t.strategy())).orElse(false);
            if (isCopy) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                // Folia does not support sync global tasks; fall back to GlobalRegionScheduler.
                try {
                    Bukkit.getScheduler().runTask(plugin, task);
                } catch (UnsupportedOperationException e) {
                    plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
                }
            }
        } else {
            task.run();
        }
    }

    public int poolSize(String templateId) {
        Deque<ArenaInstance> q = pool.get(templateId);
        return q == null ? 0 : q.size();
    }

    /**
     * Unload + delete every tracked arena world, then sweep the server
     * world container for orphan {@code pvpindex_*} folders left over from
     * previous crashes.
     */
    public void shutdown() {
        // Drain pool
        Set<String> worlds = new LinkedHashSet<>(trackedWorlds);
        for (Deque<ArenaInstance> q : pool.values()) {
            for (ArenaInstance instance : q) {
                worlds.add(instance.worldName());
            }
        }
        pool.clear();
        for (String worldName : worlds) {
            unloadAndDelete(worldName);
        }
        trackedWorlds.clear();
        sweepOrphans();
    }

    /** Delete any {@code pvpindex_*} folders sitting in the world container. */
    public void sweepOrphans() {
        if (Bukkit.getServer() == null) return;
        Path container = Bukkit.getWorldContainer().toPath();
        try (var stream = Files.list(container)) {
            Set<Path> candidates = new HashSet<>();
            stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(WORLD_PREFIX))
                    .filter(p -> Files.exists(p.resolve("level.dat"))
                            || Files.exists(p.resolve("region")))
                    .forEach(candidates::add);
            for (Path p : candidates) {
                String name = p.getFileName().toString();
                unloadAndDelete(name);
            }
            if (!candidates.isEmpty()) {
                plugin.getLogger().info("[ArenaPool] Swept " + candidates.size()
                        + " orphan arena world folder(s).");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[ArenaPool] Orphan sweep failed: " + e.getMessage());
        }
    }

    private void unloadAndDelete(String worldName) {
        if (Bukkit.getServer() != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                try {
                    Bukkit.unloadWorld(world, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("[ArenaPool] Unload failed for '"
                            + worldName + "': " + e.getMessage());
                }
            }
        }
        Path dir = Bukkit.getServer() == null
                ? plugin.getDataFolder().toPath().resolve("runtime-worlds").resolve(worldName)
                : Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            plugin.getLogger().warning("[ArenaPool] Delete failed for '"
                    + worldName + "': " + e.getMessage());
        }
    }
}
