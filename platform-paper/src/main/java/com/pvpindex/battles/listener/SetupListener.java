package com.pvpindex.battles.listener;

import com.pvpindex.battles.config.ConfigManager;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.util.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends a first-time setup guide to any OP who joins while the plugin still
 * has the default placeholder API keys in config.yml.  The message is
 * intentionally chatty and self-contained so a new server owner can follow
 * the steps without leaving the game.
 */
public class SetupListener implements Listener {
    private static final String PLACEHOLDER = "change-me";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageService messageService;

    public SetupListener(JavaPlugin plugin, ConfigManager configManager, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        PluginSettings settings = configManager.settings();
        if (settings == null) return;
        if (!PLACEHOLDER.equals(settings.apiKey())) return;

        // Delay by 1 tick so the join sequence completes before we spam the chat.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendGuide(player), 20L);
    }

    private void sendGuide(Player player) {
		player.sendMessage("");
		messageService.sendRaw(player, "setup.separator");
		messageService.sendRaw(player, "setup.title");
		messageService.sendRaw(player, "setup.separator");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step1_heading");
		messageService.sendRaw(player, "setup.step1_desc");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step1_yaml_key");
		messageService.sendRaw(player, "setup.step1_yaml_value");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step1_token_info");
		messageService.sendRaw(player, "setup.step1_howto");
		messageService.sendRaw(player, "setup.step1_apply");
		messageService.sendRaw(player, "setup.step1_wait");
		messageService.sendRaw(player, "setup.step1_wait2");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step2_heading");
		messageService.sendRaw(player, "setup.step2_desc");
		messageService.sendRaw(player, "setup.step2_path");
		messageService.sendRaw(player, "setup.step2_register");
		messageService.sendRaw(player, "setup.step2_copy");
		messageService.sendRaw(player, "setup.step2_schematic");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step3_heading");
		messageService.sendRaw(player, "setup.step3_desc");
		player.sendMessage("");
		messageService.sendRaw(player, "setup.step3_hint");
		messageService.sendRaw(player, "setup.separator");
		player.sendMessage("");
    }
}

