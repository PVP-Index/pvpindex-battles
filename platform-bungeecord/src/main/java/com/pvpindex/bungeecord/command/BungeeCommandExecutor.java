package com.pvpindex.bungeecord.command;

import com.pvpindex.bungeecord.PvPIndexBungeePlugin;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.node.ProxyNode;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public final class BungeeCommandExecutor extends Command {

    private final PvPIndexBungeePlugin plugin;

    public BungeeCommandExecutor(PvPIndexBungeePlugin plugin) {
        super("pvpindex", "pvpindex.admin", "pvi");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(text("&6PvPIndex BungeeCord Proxy &7v1.0.0"));
            sender.sendMessage(text("&7Use /pvpindex network to view network status."));
            return;
        }

        if ("network".equalsIgnoreCase(args[0])) {
            handleNetwork(sender);
        } else if ("reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(text("&eConfig reload is not yet supported on BungeeCord. Restart the proxy."));
        } else {
            sender.sendMessage(text("&cUnknown sub-command: " + args[0]));
        }
    }

    private void handleNetwork(CommandSender sender) {
        NetworkRouter router = plugin.networkRouter();
        if (router == null) {
            sender.sendMessage(text("&cMulti-proxy networking is disabled."));
            return;
        }

        sender.sendMessage(text("&6--- PvPIndex Network Status ---"));
        sender.sendMessage(text("&7Local proxy: &f" + plugin.config().networkConfig().proxyId()
                + " &7(" + plugin.config().networkConfig().region() + ")"));
        sender.sendMessage(text("&7Online proxies:"));

        for (ProxyNode node : router.onlineProxies()) {
            sender.sendMessage(text("  &a" + node.proxyId()
                    + " &7region=" + node.region()
                    + " players=" + node.playerCount()));
        }

        sender.sendMessage(text("&7Global players: &f" + router.playerRegistry().globalPlayerCount()));
        sender.sendMessage(text("&7Global servers: &f" + router.serverRegistry().globalServerCount()));
    }

    private TextComponent text(String msg) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', msg));
    }
}
