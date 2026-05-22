package com.pvpindex.battles.listener;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.util.MessageService;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks command execution while a player is in an active battle.
 * Configurable via {@code battle_commands} in config.yml.
 * Players with {@code pvpindex.battle.commands.bypass} skip the check.
 */
public class BattleCommandBlockListener implements Listener {

    private static final String BYPASS_PERMISSION = "pvpindex.battle.commands.bypass";

    private final BattleService battleService;
    private final PluginSettings settings;
    private final MessageService messageService;

    public BattleCommandBlockListener(BattleService battleService, PluginSettings settings, MessageService messageService) {
        this.battleService = battleService;
        this.settings = settings;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!settings.blockCommandsInBattle()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        if (!battleService.hasActiveBattle(player.getUniqueId())) {
            return;
        }

        String raw = event.getMessage().substring(1).trim();
        String rootCommand = raw.contains(" ") ? raw.substring(0, raw.indexOf(' ')) : raw;
        rootCommand = rootCommand.toLowerCase();

        if (isAllowed(rootCommand)) {
            return;
        }

        event.setCancelled(true);
        messageService.send(player, "battle.command_blocked");
    }

    private boolean isAllowed(String rootCommand) {
        List<String> allowed = settings.allowedBattleCommands();
        for (String cmd : allowed) {
            if (cmd.equals(rootCommand)) {
                return true;
            }
        }
        return false;
    }
}
