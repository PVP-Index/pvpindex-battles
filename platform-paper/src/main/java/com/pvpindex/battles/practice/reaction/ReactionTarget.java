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
 * <p>Visually each target is a small invisible {@link ArmorStand} surrounded
 * by layered particle effects:</p>
 * <ul>
 *   <li>Countdown ring — up to {@value #MAX_STARS} fixed END_ROD stars that
 *       disappear one-by-one as the target ages, giving a clear remaining-time
 *       indicator.</li>
 *   <li>Hitbox ring — six DUST dots at the click radius (≈ 0.35 blocks) that
 *       shift from cyan → orange → red as the target approaches expiry.</li>
 *   <li>Core glow — a tight GLOW / END_ROD cluster at the exact centre so
 *       the player always knows where to aim.</li>
 * </ul>
 *
 * <p>When hit, {@link #remove(boolean)} with {@code burst=true} spawns a
 * confetti explosion and plays a satisfying sound combination.</p>
 */
public final class ReactionTarget {

    /** PDC key placed on every reaction-target ArmorStand. */
    public static final NamespacedKey PDC_KEY = new NamespacedKey("pvpindex", "reaction_target");

    /** Number of countdown stars shown at full lifetime. */
    private static final int MAX_STARS = 8;

    private final ArmorStand stand;
    private final long spawnTimeMs;
    /** Total lifetime in milliseconds — used to compute the age ratio. */
    private final long lifetimeMs;

    // Either Folia ScheduledTask or legacy BukkitTask – only one will be non-null.
    private ScheduledTask foliaTask;
    private BukkitTask bukkitTask;

    private volatile boolean removed = false;

    /**
     * Spawns the ArmorStand at {@code location} and immediately starts the
     * particle animation.
     *
     * @param lifetimeMs total lifetime in milliseconds; drives the countdown ring
     */
    public ReactionTarget(Plugin plugin, Location location, long lifetimeMs) {
        this.spawnTimeMs = System.currentTimeMillis();
        this.lifetimeMs  = lifetimeMs;

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

        // Age ratio: 0.0 = just spawned, 1.0 = lifetime exhausted
        double ageRatio  = lifetimeMs > 0
                ? Math.min(1.0, (double) (System.currentTimeMillis() - spawnTimeMs) / lifetimeMs)
                : 0.0;
        double remaining = 1.0 - ageRatio;

        // ─ Countdown ring: fixed END_ROD stars, disappearing one-by-one ──────
        // Stars sit at fixed angular positions so the player can watch them vanish.
        if (tick % 3 == 0) {
            int litStars = (int) Math.ceil(remaining * MAX_STARS);
            for (int i = 0; i < litStars; i++) {
                double angle = (Math.PI * 2.0 / MAX_STARS) * i;
                world.spawnParticle(Particle.END_ROD,
                        center.clone().add(Math.cos(angle) * 0.65, 0, Math.sin(angle) * 0.65),
                        1, 0, 0, 0, 0);
            }
        }

        // ─ Core glow: bright cluster at the exact click point ────────────────
        if (tick % 4 == 0) {
            world.spawnParticle(Particle.GLOW, center, 3, 0.05, 0.05, 0.05, 0);
        }

        // ─ Hitbox ring: DUST dots at click radius, colour = time remaining ───
        // Cyan (fresh) → orange (half-way) → red (about to expire).
        if (tick % 6 == 0) {
            org.bukkit.Color ringColor;
            if (remaining > 0.6) {
                ringColor = org.bukkit.Color.fromRGB(0, 200, 255);   // cyan
            } else if (remaining > 0.3) {
                ringColor = org.bukkit.Color.fromRGB(255, 140, 0);   // orange
            } else {
                ringColor = org.bukkit.Color.fromRGB(255, 40, 40);   // red
            }
            var dust = new Particle.DustOptions(ringColor, 0.9f);
            // Equatorial ring
            for (int i = 0; i < 6; i++) {
                double angle = (Math.PI * 2.0 / 6) * i;
                world.spawnParticle(Particle.DUST,
                        center.clone().add(Math.cos(angle) * 0.35, 0, Math.sin(angle) * 0.35),
                        1, 0, 0, 0, 0, dust);
            }
            // Top and bottom caps for 3-D depth cue
            world.spawnParticle(Particle.DUST, center.clone().add(0,  0.35, 0), 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, center.clone().add(0, -0.35, 0), 1, 0, 0, 0, 0, dust);
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
