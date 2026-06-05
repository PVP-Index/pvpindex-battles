package com.pvpindex.velocity.command;

import com.pvpindex.velocity.PvPIndexVelocityPlugin;
import com.pvpindex.velocity.registry.BattleRegistry;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Proxy-level {@code /pvpindex} command accessible to all players and the
 * console.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code /pvpindex} (no args). show help</li>
 *   <li>{@code /pvpindex global}. list all active battles across all backend servers</li>
 *   <li>{@code /pvpindex where <player>}. show which server a player is on</li>
 *   <li>{@code /pvpindex reload}. reload the proxy plugin config (console/op only)</li>
 * </ul>
 */
public final class VelocityPvPIndexCommand implements SimpleCommand {

    private static final Component PREFIX =
            Component.text("[PvPIndex] ", NamedTextColor.GOLD);

    private final PvPIndexVelocityPlugin plugin;

    public VelocityPvPIndexCommand(PvPIndexVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            showHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "global" -> showGlobal(source);
            case "where"  -> showWhere(source, args);
            case "reload" -> handleReload(source);
            default       -> showHelp(source);
        }
    }

    // -------------------------------------------------------------------------
    // Subcommand handlers
    // -------------------------------------------------------------------------

    private void showGlobal(CommandSource source) {
        Collection<BattleRegistry.BattleEntry> battles = plugin.battleRegistry().all();
        if (battles.isEmpty()) {
            source.sendMessage(PREFIX.append(
                    Component.text("No active battles across the network.", NamedTextColor.GRAY)));
            return;
        }

        // Group by server name for a cleaner display.
        Map<String, List<BattleRegistry.BattleEntry>> byServer = battles.stream()
                .collect(Collectors.groupingBy(BattleRegistry.BattleEntry::serverName));

        source.sendMessage(PREFIX.append(
                Component.text("Active battles (" + battles.size() + " total):", NamedTextColor.YELLOW)));

        byServer.forEach((server, list) -> {
            source.sendMessage(Component.text("  [" + server + "] ", NamedTextColor.AQUA)
                    .append(Component.text(list.size() + " battle(s):", NamedTextColor.WHITE)));
            for (BattleRegistry.BattleEntry e : list) {
                source.sendMessage(Component.text("    · ", NamedTextColor.GRAY)
                        .append(Component.text(e.battleUuid().toString().substring(0, 8) + "…",
                                NamedTextColor.DARK_AQUA))
                        .append(Component.text(". ", NamedTextColor.GRAY))
                        .append(Component.text(e.participantUuids().size() + " players",
                                NamedTextColor.WHITE)));
            }
        });
    }

    private void showWhere(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(PREFIX.append(
                    Component.text("Usage: /pvpindex where <player>", NamedTextColor.RED)));
            return;
        }
        String targetName = args[1];
        plugin.getServer().getPlayer(targetName).ifPresentOrElse(
                player -> {
                    String server = player.getCurrentServer()
                            .map(c -> c.getServerInfo().getName())
                            .orElse("limbo/connecting");
                    String battleInfo = plugin.battleRegistry().getBattleForPlayer(player.getUniqueId())
                            .map(b -> " (in battle " + b.battleUuid().toString().substring(0, 8) + "…)")
                            .orElse("");
                    source.sendMessage(PREFIX
                            .append(Component.text(player.getUsername(), NamedTextColor.AQUA))
                            .append(Component.text(" is on ", NamedTextColor.WHITE))
                            .append(Component.text(server, NamedTextColor.GREEN))
                            .append(Component.text(battleInfo, NamedTextColor.YELLOW)));
                },
                () -> source.sendMessage(PREFIX.append(
                        Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED)))
        );
    }

    private void handleReload(CommandSource source) {
        if (source instanceof Player player && !player.hasPermission("pvpindex.admin")) {
            source.sendMessage(PREFIX.append(
                    Component.text("You don't have permission to do that.", NamedTextColor.RED)));
            return;
        }
        plugin.reloadConfig();
        source.sendMessage(PREFIX.append(
                Component.text("Config reloaded.", NamedTextColor.GREEN)));
    }

    private void showHelp(CommandSource source) {
        source.sendMessage(PREFIX.append(Component.text("Commands:", NamedTextColor.YELLOW)));
        source.sendMessage(Component.text("  /pvpindex global", NamedTextColor.AQUA)
                .append(Component.text(". show all active battles across the network", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("  /pvpindex where <player>", NamedTextColor.AQUA)
                .append(Component.text(". show which server a player is on", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("  /pvpindex reload", NamedTextColor.AQUA)
                .append(Component.text(". reload proxy plugin config (admin only)", NamedTextColor.GRAY)));
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return CompletableFuture.completedFuture(
                    List.of("global", "where", "reload").stream()
                            .filter(s -> s.startsWith(prefix))
                            .collect(Collectors.toList()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("where")) {
            String prefix = args[1].toLowerCase();
            return CompletableFuture.completedFuture(
                    plugin.getServer().getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList()));
        }
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
