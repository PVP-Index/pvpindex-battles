package com.pvpindex.battles.practice.bot;

import com.pvpindex.battles.gamemode.KitApplier;
import com.pvpindex.battles.gamemode.KitDefinition;
import java.lang.reflect.Method;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Thin wrapper around the bot entity spawned by {@link BotPlayerFactory}.
 *
 * <p>Provides a clean API for the rest of the practice system without leaking
 * the NMS-vs-Zombie distinction.  When the underlying entity is a fake NMS
 * {@link Player}, movement is performed via reflection on the NMS
 * {@code setPos()} method (connection-safe).  For a {@link org.bukkit.entity.Zombie}
 * fallback, standard {@code entity.teleport()} is used.</p>
 */
public final class BotOpponent {

    static final double MAX_HEALTH = 20.0;

    private final LivingEntity entity;
    /** Cached NMS entity handle for connection-free position updates. */
    private Object nmsHandle;

    public BotOpponent(LivingEntity entity) {
        this.entity = entity;
        cacheNmsHandle();
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Apply a {@link KitDefinition} to the bot entity.
     *
     * <p>For fake-player entities (no real connection) we set equipment via
     * the NMS-level equipment slots when possible, falling back to the Bukkit
     * {@link EntityEquipment} API which still works for inventory storage
     * even without a connection for the client-visible sync (entity tracker
     * will broadcast the equipment on the next tracking interval).</p>
     */
    public void applyKit(KitDefinition kit, KitApplier kitApplier) {
        if (entity instanceof Player player) {
            kitApplier.apply(player, kit);
        } else {
            // Zombie fallback – set equipment directly
            EntityEquipment eq = entity.getEquipment();
            if (eq == null) return;
            eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            eq.setHelmet(new ItemStack(Material.IRON_HELMET));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
        }
        resetHealth();
    }

    /** Restore the bot to full health. */
    public void resetHealth() {
        try {
            var attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(MAX_HEALTH);
        } catch (Exception ignored) {}
        entity.setHealth(MAX_HEALTH);
    }

    // ── Movement ──────────────────────────────────────────────────────────

    /**
     * Move the bot to a new location.
     *
     * <p>For a fake NMS player (no connection) calling {@code player.teleport()}
     * would NPE when trying to send the teleport packet.  We instead call
     * {@code ServerPlayer.setPos()} directly via the cached NMS handle,
     * which updates the server-side position; the entity tracker then
     * broadcasts a position-sync packet on the next tick automatically.</p>
     */
    public void moveTo(Location loc) {
        if (nmsHandle != null) {
            try {
                nmsHandle.getClass()
                        .getMethod("setPos", double.class, double.class, double.class)
                        .invoke(nmsHandle, loc.getX(), loc.getY(), loc.getZ());
                // Update yaw / pitch
                nmsHandle.getClass().getMethod("setYRot", float.class).invoke(nmsHandle, loc.getYaw());
                nmsHandle.getClass().getMethod("setXRot", float.class).invoke(nmsHandle, loc.getPitch());
                try {
                    nmsHandle.getClass().getMethod("setYBodyRot", float.class)
                            .invoke(nmsHandle, loc.getYaw());
                } catch (Exception ignored) {}
                return;
            } catch (Exception e) {
                nmsHandle = null; // invalidate cache, fall through to Bukkit teleport
            }
        }
        // Bukkit fallback (Zombie or NMS fallback)
        entity.teleport(loc);
    }

    /** Make the entity look at {@code target}. */
    public void lookAt(Location target) {
        Location current = entity.getLocation();
        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double dy = target.getY() - current.getY() + 1.0;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        Location looked = current.clone();
        looked.setYaw(yaw);
        looked.setPitch(pitch);
        moveTo(looked);
    }

    // ── Combat ────────────────────────────────────────────────────────────

    public double getHealth()                   { return entity.getHealth(); }
    public void setHealth(double hp)            { entity.setHealth(Math.max(0, hp)); }
    public boolean isDead()                     { return !entity.isValid() || entity.isDead() || entity.getHealth() <= 0; }

    /** Play the bot's melee-attack swing sounds and particles near {@code target}. */
    public void playAttackEffect(Location target) {
        var world = target.getWorld();
        if (world == null) return;
        world.playSound(target, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 1.0f);
        world.spawnParticle(org.bukkit.Particle.CRIT, target.add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.05);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    /** Remove the bot entity from the world. */
    public void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public LivingEntity entity()    { return entity; }
    public UUID entityUuid()        { return entity.getUniqueId(); }
    public Location location()      { return entity.getLocation(); }
    public boolean isNmsPlayer()    { return entity instanceof Player; }

    // ── Internal ─────────────────────────────────────────────────────────

    /** Cache the NMS entity handle (if accessible) for connection-free movement. */
    private void cacheNmsHandle() {
        if (!(entity instanceof Player)) return; // only needed for fake-player
        try {
            nmsHandle = entity.getClass().getMethod("getHandle").invoke(entity);
        } catch (Exception e) {
            nmsHandle = null;
        }
    }
}
