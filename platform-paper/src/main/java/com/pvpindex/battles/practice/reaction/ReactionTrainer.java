package com.pvpindex.battles.practice.reaction;

import com.pvpindex.battles.practice.PracticeSession;
import com.pvpindex.battles.practice.PracticeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Orchestrates a reaction-training session for one player.
 *
 * <p>Targets are spawned at a configurable interval (default 40 ticks / 2 s)
 * at random offsets around the player's current eye position.  Targets that
 * are not hit within their lifetime are counted as misses and removed silently.
 * The player's score is displayed live on the action bar after every event.</p>
 */
public final class ReactionTrainer {

    /** Maximum angular offset (in blocks) when randomly positioning a target. */
    private static final double SPREAD_H = 4.5;
    private static final double SPREAD_V = 1.2;

    private final Plugin plugin;
    private final PracticeSettings settings;
    private final ReactionScoreTracker score = new ReactionScoreTracker();
    private final List<ReactionTarget> activeTargets = new ArrayList<>();
    private final Random random = new Random();

    // Session data
    private Player player;
    private PracticeSession session;

    // Scheduler tasks
    private BukkitTask spawnTask;
    private BukkitTask expireTask;

    private boolean running = false;

    public ReactionTrainer(Plugin plugin, PracticeSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Begin a new training session for the given player. */
    public void start(Player player, PracticeSession session) {
        if (running) stop();
        this.player = player;
        this.session = session;
        this.running = true;

        // Target-spawn loop
        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !running) {
                stop();
                return;
            }
            if (activeTargets.size() < settings.reactionMaxTargets()) {
                spawnTarget();
            }
        }, settings.reactionSpawnIntervalTicks(), settings.reactionSpawnIntervalTicks());

        // Expiry-check loop (runs every 10 ticks to avoid tick-perfect timing issues)
        expireTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;
            long now = System.currentTimeMillis();
            long lifetimeMs = settings.reactionTargetLifetimeTicks() * 50L;
            List<ReactionTarget> expired = activeTargets.stream()
                    .filter(t -> !t.isRemoved() && (now - t.spawnTimeMs()) >= lifetimeMs)
                    .toList();
            for (ReactionTarget t : expired) {
                t.remove(false); // silent — no burst for a miss
                activeTargets.remove(t);
                score.recordMiss();
            }
            if (player.isOnline()) {
                player.sendActionBar(score.formatActionBar());
            }
        }, 10L, 10L);
    }

    /** Stop the session, remove all remaining targets, and show the summary. */
    public void stop() {
        if (!running) return;
        running = false;
        if (spawnTask  != null) spawnTask.cancel();
        if (expireTask != null) expireTask.cancel();
        for (ReactionTarget t : List.copyOf(activeTargets)) {
            t.remove(false);
        }
        activeTargets.clear();
        if (player != null && player.isOnline()) {
            player.clearTitle();
            player.showTitle(Title.title(
                    score.formatSummary(),
                    net.kyori.adventure.text.Component.empty(),
                    Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofSeconds(5),
                            java.time.Duration.ofMillis(500))));
        }
    }

    /**
     * Called by {@link com.pvpindex.battles.listener.PracticeListener} when the
     * player successfully lands a sword hit on a reaction target.
     *
     * @param target the target that was hit
     */
    public void onTargetHit(ReactionTarget target) {
        if (!running || target.isRemoved()) return;
        long reactionMs = target.reactionTimeMs();
        target.remove(true); // burst!
        activeTargets.remove(target);
        score.recordHit(reactionMs);
        if (player != null && player.isOnline()) {
            player.sendActionBar(score.formatActionBar());
        }
    }

    // ── Target spawning ───────────────────────────────────────────────────

    private void spawnTarget() {
        if (!player.isOnline()) return;
        Location eye = player.getEyeLocation();

        // Random horizontal offset within a hemisphere in front of the player
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 2.5 + random.nextDouble() * SPREAD_H;
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        double offsetY = (random.nextDouble() - 0.5) * SPREAD_V * 2;

        Location spawnLoc = eye.clone().add(offsetX, offsetY - 0.9, offsetZ);
        // Keep Y above ground
        spawnLoc.setY(Math.max(spawnLoc.getY(), eye.getY() - 1.2));

        activeTargets.add(new ReactionTarget(plugin, spawnLoc));
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public boolean isRunning()                      { return running; }
    public ReactionScoreTracker score()             { return score; }
    public List<ReactionTarget> activeTargets()     { return List.copyOf(activeTargets); }
    public UUID playerUuid()                        { return player != null ? player.getUniqueId() : null; }
}
