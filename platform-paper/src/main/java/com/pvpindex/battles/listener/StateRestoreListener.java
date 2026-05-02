package com.pvpindex.battles.listener;

import com.pvpindex.battles.battle.PlayerStateService;
import com.pvpindex.battles.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * On player join, if a battle-state snapshot exists for them on disk
 * (left over from a mid-battle disconnect or server crash), restore it
 * so they don't lose inventory or end up stranded in a deleted arena
 * world.
 */
public final class StateRestoreListener implements Listener {

    private final Plugin plugin;
    private final PlayerStateService stateService;
    private final MessageService messageService;

    public StateRestoreListener(Plugin plugin, PlayerStateService stateService, MessageService messageService) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!stateService.hasSnapshot(event.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            boolean restored = stateService.restore(event.getPlayer());
            if (restored) {
                messageService.sendRaw(event.getPlayer(), "moderation.state_restored");
            }
        });
    }
}
