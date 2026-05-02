package com.pvpindex.battles.moderation;

import com.pvpindex.battles.battle.BattleService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Tab completion for the {@code /pvpmod} command.
 *
 * <pre>
 *   /pvpmod &lt;TAB&gt;  →  watch | replay | report | reports | ban | unban
 *
 *   /pvpmod watch   &lt;battleUuid&gt;
 *   /pvpmod replay  &lt;battleUuid|stop&gt;
 *   /pvpmod report  &lt;playerName&gt; &lt;reason…&gt;
 *   /pvpmod ban     [federated] &lt;playerName&gt; &lt;duration|perm&gt; &lt;reason…&gt;
 *   /pvpmod unban   &lt;playerName&gt;
 * </pre>
 */
public final class ModerationTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS = List.of(
            "watch", "replay", "report", "reports", "ban", "unban");

    private static final List<String> DURATION_EXAMPLES = List.of(
            "30m", "1h", "6h", "12h", "1d", "7d", "30d", "perm");

    private final BattleService battleService;

    public ModerationTabCompleter(BattleService battleService) {
        this.battleService = battleService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
            String alias, String[] args) {

        if (!sender.hasPermission("pvpindex.mod") && !sender.hasPermission("pvpindex.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "watch" -> args.length == 2 ? activeBattleUuids(args[1]) : List.of();

            case "replay" -> {
                if (args.length == 2) {
                    var uuids = activeBattleUuids(args[1]);
                    // also suggest "stop"
                    var stop = filter(List.of("stop"), args[1]);
                    yield concat(uuids, stop);
                }
                yield List.of();
            }

            case "report" -> args.length == 2 ? onlinePlayerNames(args[1]) : List.of();

            case "ban" -> switch (args.length) {
                case 2 -> {
                    // first arg after "ban": "federated" or a player name
                    var fed = filter(List.of("federated"), args[1]);
                    var names = onlinePlayerNames(args[1]);
                    yield concat(fed, names);
                }
                case 3 -> {
                    if ("federated".equalsIgnoreCase(args[1])) {
                        // /pvpmod ban federated <player>
                        yield onlinePlayerNames(args[2]);
                    }
                    // /pvpmod ban <player> <duration>
                    yield filter(DURATION_EXAMPLES, args[2]);
                }
                case 4 -> {
                    if ("federated".equalsIgnoreCase(args[1])) {
                        // /pvpmod ban federated <player> <duration>
                        yield filter(DURATION_EXAMPLES, args[3]);
                    }
                    yield List.of();
                }
                default -> List.of();
            };

            case "unban" -> args.length == 2 ? onlinePlayerNames(args[1]) : List.of();

            default -> List.of();
        };
    }

    // -------------------------------------------------------------------------

    private List<String> activeBattleUuids(String partial) {
        String lower = partial.toLowerCase();
        return battleService.activeBattles().stream()
                .map(s -> s.getUuid().toString())
                .filter(u -> u.startsWith(lower))
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

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
