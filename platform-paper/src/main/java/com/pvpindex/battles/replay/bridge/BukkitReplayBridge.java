package com.pvpindex.battles.replay.bridge;

import com.pvpindex.battles.replay.PacketReplayBridge;
import com.pvpindex.battles.replay.ReplayFrame;
import com.pvpindex.battles.util.MessageService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Default, Bukkit-only implementation of {@link PacketReplayBridge}.
 *
 * <p>This is intentionally limited (it spawns visible armor stands as
 * placeholders for the recorded players). Production deployments should
 * register an NMS / PacketEvents bridge that emits real
 * {@code ClientboundAddPlayerPacket} +
 * {@code ClientboundPlayerInfoUpdatePacket} traffic. see
 * {@link PacketReplayBridge} javadoc.</p>
 */
public final class BukkitReplayBridge implements PacketReplayBridge {
    private final Plugin plugin;
    private final MessageService messageService;
    private final Map<UUID, Map<UUID, ArmorStand>> ghosts = new HashMap<>();

    public BukkitReplayBridge(Plugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    @Override
    public void spawnGhost(Player viewer, UUID ghostId, ReplayFrame initialFrame) {
        runOnMain(() -> {
            Location loc = new Location(viewer.getWorld(), initialFrame.x(), initialFrame.y(), initialFrame.z(),
                    initialFrame.yaw(), initialFrame.pitch());
            ArmorStand stand = viewer.getWorld().spawn(loc, ArmorStand.class, s -> {
                s.setInvulnerable(true);
                s.setGravity(false);
                s.setBasePlate(false);
                s.setCustomNameVisible(true);
                String label = messageService.raw("replay.ghost_label", "%id%", ghostId.toString().substring(0, 8))
                        .replace('&', '\u00a7');
                s.setCustomName(label);
                s.setCanPickupItems(false);
                s.setCollidable(false);
            });
            ghosts.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>()).put(ghostId, stand);
        });
    }

    @Override
    public void applyFrame(Player viewer, UUID ghostId, ReplayFrame frame) {
        ArmorStand stand = lookup(viewer, ghostId);
        if (stand == null || stand.isDead()) {
            return;
        }
        runOnMain(() -> stand.teleport(new Location(viewer.getWorld(), frame.x(), frame.y(), frame.z(), frame.yaw(), frame.pitch())));
    }

    @Override
    public void emitAnimation(Player viewer, UUID ghostId, String animation) {
        ArmorStand stand = lookup(viewer, ghostId);
        if (stand == null) return;
        // No-op for the placeholder bridge; real bridge sends ClientboundAnimatePacket.
        if ("death".equals(animation)) {
            runOnMain(stand::remove);
        }
    }

    @Override
    public void destroyGhost(Player viewer, UUID ghostId) {
        Map<UUID, ArmorStand> perViewer = ghosts.get(viewer.getUniqueId());
        if (perViewer == null) return;
        ArmorStand stand = perViewer.remove(ghostId);
        if (stand != null) {
            runOnMain(stand::remove);
        }
    }

    private ArmorStand lookup(Player viewer, UUID ghostId) {
        Map<UUID, ArmorStand> perViewer = ghosts.get(viewer.getUniqueId());
        return perViewer == null ? null : perViewer.get(ghostId);
    }

    private void runOnMain(Runnable runnable) {
        if (plugin.getServer() != null && !plugin.getServer().isPrimaryThread()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTask(plugin);
        } else {
            runnable.run();
        }
    }

    // EntityType reference kept to make the dependency on Bukkit entities explicit for IDE navigation.
    @SuppressWarnings("unused")
    private static final EntityType GHOST_TYPE = EntityType.ARMOR_STAND;
}
