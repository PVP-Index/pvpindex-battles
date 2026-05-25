package com.pvpindex.battles.practice.bot;

import com.pvpindex.battles.practice.PracticeSettings;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drives the bot's movement and attack AI.
 *
 * <p>The task runs every 2 ticks (100 ms) on the Bukkit scheduler. On each
 * tick it:</p>
 * <ol>
 *   <li>Computes the direction vector from the bot to the target player.</li>
 *   <li>Applies a perpendicular strafe offset that flips every 60 ticks,
 *       giving the bot a realistic side-to-side movement pattern.</li>
 *   <li>Steps the bot toward the computed destination at
 *       {@link PracticeSettings#botMoveSpeed()} blocks/tick.</li>
 *   <li>Every {@link PracticeSettings#botAttackIntervalTicks()} ticks, deals
 *       damage to the target player and plays melee-hit effects.</li>
 * </ol>
 *
 * <p>If the bot HP drops below 20 % it enters a brief retreat phase
 * (20 ticks) to simulate mid-level defensive play.</p>
 */
public final class BotBehaviorTask {

    private static final double IDEAL_DISTANCE   = 2.0;  // blocks — sweet spot for melee
    private static final double STOP_DISTANCE    = 1.3;  // closer than this: don't advance
    private static final double RETREAT_DISTANCE = 4.5;  // retreat target distance
    private static final double RETREAT_HP_FRAC  = 0.20; // retreat below 20 % HP
    private static final int    STRAFE_FLIP_TICKS = 60;  // ticks before strafe direction flips

    private final Plugin plugin;
    private final BotOpponent bot;
    private final PracticeSettings settings;

    /** Target player UUID – looked up from Bukkit on each tick. */
    private final UUID targetUuid;

    private BukkitTask task;
    private int tickCount      = 0;
    private int strafeDirection = 1;  // +1 or -1
    private int retreatTicks    = 0;
    private boolean running     = false;

    public BotBehaviorTask(Plugin plugin, BotOpponent bot, Player target, PracticeSettings settings) {
        this.plugin = plugin;
        this.bot = bot;
        this.settings = settings;
        this.targetUuid = target.getUniqueId();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Start the AI task. Safe to call only once per instance. */
    public void start() {
        if (running) return;
        running = true;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    /** Cancel the task. The bot entity is NOT removed here. */
    public void stop() {
        running = false;
        if (task != null) task.cancel();
    }

    // ── Per-tick logic ────────────────────────────────────────────────────

    private void tick() {
        if (!running) return;
        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            stop();
            return;
        }
        if (bot.isDead() || !bot.entity().isValid()) {
            stop();
            return;
        }

        tickCount++;

        // Flip strafe every STRAFE_FLIP_TICKS ticks
        if (tickCount % STRAFE_FLIP_TICKS == 0) {
            strafeDirection = -strafeDirection;
        }

        Location botLoc    = bot.location();
        Location targetLoc = target.getLocation();
        double dist = botLoc.distance(targetLoc);

        // ── Attack ────────────────────────────────────────────────────────
        if (tickCount % settings.botAttackIntervalTicks() == 0 && dist <= IDEAL_DISTANCE + 0.8) {
            target.damage(settings.botAttackDamage());
            bot.playAttackEffect(targetLoc);
        }

        // ── Retreat if low HP ─────────────────────────────────────────────
        double maxHp = BotOpponent.MAX_HEALTH;
        if (bot.getHealth() / maxHp < RETREAT_HP_FRAC && retreatTicks == 0) {
            retreatTicks = 20; // retreat for 1 second
        }
        if (retreatTicks > 0) {
            retreatTicks -= 2; // decremented every 2-tick cycle
            moveAwayFrom(botLoc, targetLoc);
            return;
        }

        // ── Approach + strafe ─────────────────────────────────────────────
        if (dist > STOP_DISTANCE) {
            moveToward(botLoc, targetLoc, dist);
        }
        bot.lookAt(targetLoc);
    }

    // ── Movement helpers ──────────────────────────────────────────────────

    private void moveToward(Location from, Location target, double dist) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        // Normalize horizontal direction
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return;
        dx /= len;
        dz /= len;

        // Perpendicular strafe component
        double sx = -dz * strafeDirection * 0.3;
        double sz =  dx * strafeDirection * 0.3;

        double speed = settings.botMoveSpeed();
        if (dist < IDEAL_DISTANCE + 0.5) speed *= 0.5; // slow down near target

        double newX = from.getX() + (dx + sx) * speed;
        double newZ = from.getZ() + (dz + sz) * speed;

        Location dest = from.clone();
        dest.setX(newX);
        dest.setZ(newZ);
        // Inherit yaw toward target
        double ydx = target.getX() - newX;
        double ydz = target.getZ() - newZ;
        dest.setYaw((float) Math.toDegrees(Math.atan2(-ydx, ydz)));

        bot.moveTo(dest);
    }

    private void moveAwayFrom(Location from, Location threat) {
        double dx = from.getX() - threat.getX();
        double dz = from.getZ() - threat.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return;
        dx /= len;
        dz /= len;

        double newX = from.getX() + dx * settings.botMoveSpeed() * 1.4;
        double newZ = from.getZ() + dz * settings.botMoveSpeed() * 1.4;
        Location dest = from.clone();
        dest.setX(newX);
        dest.setZ(newZ);
        bot.moveTo(dest);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public boolean isRunning() { return running; }
}
