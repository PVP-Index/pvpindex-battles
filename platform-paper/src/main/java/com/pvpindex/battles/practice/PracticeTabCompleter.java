package com.pvpindex.battles.practice;

import com.pvpindex.battles.gamemode.GameModeRegistry;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Tab-completer for the {@code /practice} command.
 */
public final class PracticeTabCompleter implements TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reaction", "bot", "stop");

    private final GameModeRegistry gameModeRegistry;

    public PracticeTabCompleter(GameModeRegistry gameModeRegistry) {
        this.gameModeRegistry = gameModeRegistry;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reaction") || args[0].equalsIgnoreCase("bot"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return gameModeRegistry.allModes().stream()
                    .map(m -> m.id().toLowerCase(Locale.ROOT))
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
