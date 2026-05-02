package com.pvpindex.battles.command;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Tab completion for the {@code /pvpindex} command.
 *
 * <pre>
 *   /pvpindex &lt;TAB&gt;
 *       reload | verify | battles | submissions | retryfailed | sync | battle | replay
 *
 *   /pvpindex battle &lt;TAB&gt;
 *       create | start | cancel | finish | submit | dispute
 *
 *   /pvpindex battle create &lt;BattleType&gt; &lt;GameModeType&gt; &lt;player...&gt;
 *   /pvpindex battle start|cancel|finish|submit|dispute &lt;battleUuid&gt;
 *   /pvpindex replay export &lt;battleUuid&gt;
 * </pre>
 */
public final class PvPIndexTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS = List.of(
            "reload", "verify", "battles", "submissions", "retryfailed", "sync", "battle", "replay");

    private static final List<String> BATTLE_SUBS = List.of(
            "create", "start", "cancel", "finish", "submit", "dispute");

    private static final List<String> BATTLE_TYPES = Arrays.stream(BattleType.values())
            .map(Enum::name).toList();

    private static final List<String> GAME_MODE_TYPES = Arrays.stream(GameModeType.values())
            .map(Enum::name).toList();

    private final BattleService battleService;

    public PvPIndexTabCompleter(BattleService battleService) {
        this.battleService = battleService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
            String alias, String[] args) {

        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase();

        // /pvpindex battle <sub> ...
        if (sub.equals("battle")) {
            if (args.length == 2) return filter(BATTLE_SUBS, args[1]);

            String battleSub = args[1].toLowerCase();
            return switch (battleSub) {
                case "create" -> switch (args.length) {
                    case 3 -> filter(BATTLE_TYPES, args[2]);
                    case 4 -> filter(GAME_MODE_TYPES, args[3]);
                    default -> onlinePlayerNames(args[args.length - 1]);
                };
                case "start", "cancel", "finish", "submit" ->
                        args.length == 3 ? activeBattleUuids(args[2]) : List.of();
                case "dispute" ->
                        args.length == 3 ? activeBattleUuids(args[2]) : List.of();
                default -> List.of();
            };
        }

        // /pvpindex replay export <uuid>
        if (sub.equals("replay")) {
            if (args.length == 2) return filter(List.of("export"), args[1]);
            if (args.length == 3) return activeBattleUuids(args[2]);
        }

        return List.of();
    }

    // -------------------------------------------------------------------------

    private List<String> activeBattleUuids(String partial) {
        return battleService.activeBattles().stream()
                .map(s -> s.getUuid().toString())
                .filter(u -> u.startsWith(partial.toLowerCase()))
                .toList();
    }

    private static List<String> onlinePlayerNames(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(n -> n.toLowerCase().startsWith(lower))
                .toList();
    }

    private static List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .toList();
    }
}
