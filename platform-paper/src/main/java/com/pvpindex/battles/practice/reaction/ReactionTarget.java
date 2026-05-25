package com.pvpindex.battles.practice.reaction;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A single hittable reaction-training target.
 *
 * <p>Visually, each target is a small invisible {@link ArmorStand} surrounded
 * by layered particle effects:</p>
 * <ul>
 *   <li>Continuous white {@code END_ROD} glow</li>
 *   <li>Rotating cyan {@code WAX_ON} halo ring (8-point, sin/cos)</li>
 *   <li>Pulsing aqua {@code FIREWORK} core</li>
 * </ul>
 *
 * <p>When a player sword-hits the ArmorStand, {@link #remove(boolean)} is called
 * with {@code burst=true}, spawning an explosion + confetti particle burst and
 * playing a satisfying sound combination.</p>
 */
public final class ReactionTarget {

    /** PDC key placed on every reaction-target ArmorStand. */
    public static final NamespacedKey PDC_KEY = new NamespacedKey("pvpindex", "reaction_target");

    private final ArmorStand stand;
    private final long spawnTimeMs;

    // Either Folia ScheduledTask or legacy BukkitTask – only one will be non-null.
    private ScheduledTask foliaTask;
    private BukkitTask bukkitTask;

    private volatile boolean removed = false;

    /**
     * Spawns the ArmorStand at {@code location} and immediately starts the
     * particle animation.
     */
    public ReactionTarget(Plugin plugin, Location location) {
        this.spawnTimeMs = System.currentTimeMillis();

        ArmorStand as = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        as.setInvisible(true);
        as.setSmall(true);
        as.setGravity(false);
        as.setInvulnerable(false);
        as.setArms(false);
        as.setBasePlate(false);
        as.setCustomNameVisible(false);
        as.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.BYTE, (byte) 1);
        this.stand = as;

        startAnimation(plugin);
    }

    // ── Animation ────────────────────────────────────────────────────────────

    private void startAnimation(Plugin plugin) {
        AtomicInteger tick = new AtomicInteger(0);
        try {
            // Folia / Paper-Folia entity scheduler
            foliaTask = stand.getScheduler().runAtFixedRate(
                    plugin,
                    task -> {
                        if (removed || !stand.isValid()) {
                            task.cancel();
                            return;
                        }
                        animateTick(tick.getAndIncrement());
                    },
                    null, // retired callback
                    1L,   // initial delay
                    2L    // period
            );
        } catch (UnsupportedOperationException | NoSuchMethodError e) {
            // Legacy Paper without Folia API stubs
            bukkitTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (removed || !stand.isValid()) {
                    if (bukkitTask != null) bukkitTask.cancel();
                    return;
                }
                animateTick(tick.getAndIncrement());
            }, 0L, 2L);
        }
    }

    private void animateTick(int tick) {
        if (!stand.isValid()) return;
        World world = stand.getWorld();
        Location center = stand.getLocation().add(0, 0.9, 0); // chest height

        // ─ Continuous white END_ROD glow ─────────────────────────────────
        world.spawnParticle(Particle.END_ROD, center, 3, 0.12, 0.12, 0.12, 0.008);

        // ─ Rotating 8-point WAX_ON halo ring (every 4 ticks) ─────────────
        if (tick % 4 == 0) {
            double angleMod = tick * 0.18; // slow rotation
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 / 8) * i + angleMod;
                double rx = Math.cos(angle) * 0.55;
                double rz = Math.sin(angle) * 0.55;
                world.spawnParticle(Particle.WAX_ON, center.clone().add(rx, 0, rz),
                        1, 0, 0, 0, 0);
            }
        }

        // ─ Pulsing FIREWORK core (every 6 ticks) ─────────────────────────
        if (tick % 6 == 0) {
            world.spawnParticle(Particle.FIREWORK, center, 2, 0.06, 0.06, 0.06, 0.015);
        }

        // ─ Subtle vertical DUST sweep (every 10 ticks) ────────────────────
        if (tick % 10 == 0) {
            var dustOptions = new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 255), 0.8f);
            world.spawnParticle(Particle.DUST, center, 4, 0.2, 0.3, 0.2, 0, dustOptions);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Removes this target. When {@code burst} is {@code true} a confetti
     * particle explosion and hit sounds are played before despawning.
     */
    public void remove(boolean burst) {
        if (removed) return;
        removed = true;
        cancelAnimationTask();
        if (stand.isValid()) {
            if (burst) {
                spawnHitBurst();
            }
            stand.remove();
        }
    }

    private void cancelAnimationTask() {
        if (foliaTask != null)   foliaTask.cancel();
        if (bukkitTask != null)  bukkitTask.cancel();
    }

    private void spawnHitBurst() {
        Location loc = stand.getLocation().add(0, 0.9, 0);
        World world = stand.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        world.spawnParticle(Particle.FIREWORK, loc, 25, 0.45, 0.45, 0.45, 0.1);
        world.spawnParticle(Particle.END_ROD,  loc, 14, 0.3,  0.3,  0.3,  0.07);
        var lime = new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 255, 80), 1.2f);
        world.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.3, 0.3, 0, lime);
        world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING,        0.8f, 2.0f);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Milliseconds elapsed since this target was spawned. */
    public long reactionTimeMs() {
        return System.currentTimeMillis() - spawnTimeMs;
    }

    public ArmorStand stand()   { return stand; }
    public boolean isRemoved()  { return removed; }
    public long spawnTimeMs()   { return spawnTimeMs; }
}
