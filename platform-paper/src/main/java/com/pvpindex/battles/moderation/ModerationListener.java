package com.pvpindex.battles.moderation;

import com.pvpindex.battles.util.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Blocks logins for banned players, including (when configured) bans
 * published by other verified PvPIndex servers.
 */
public final class ModerationListener implements Listener {
    private final ModerationService moderationService;
    private final MessageService messageService;

    public ModerationListener(ModerationService moderationService, MessageService messageService) {
        this.moderationService = moderationService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        moderationService.activeBan(event.getUniqueId()).ifPresent(ban -> {
            String noReason = messageService.raw("moderation.no_reason");
            String message = moderationService.settings().banScreenMessage()
                    .replace("%reason%", ban.reason() == null ? noReason : ban.reason())
                    .replace('&', '\u00a7');
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
        });
    }
}
