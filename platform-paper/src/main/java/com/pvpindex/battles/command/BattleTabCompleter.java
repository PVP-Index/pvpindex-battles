package com.pvpindex.battles.command;

import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.messaging.NetworkPlayerCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Tab completion for the {@code /battle} command tree.
 *
 * <pre>
 *   /battle &lt;TAB&gt;                     → leave, challenge, accept, decline
 *   /battle challenge &lt;TAB&gt;            → online player names (local + network)
 *   /battle challenge &lt;player&gt; &lt;TAB&gt;  → mode IDs
 *   /battle accept &lt;TAB&gt;              → pending challenge IDs for this player
 *   /battle decline &lt;TAB&gt;             → pending challenge IDs for this player
 * </pre>
 */
public final class BattleTabCompleter implements TabCompleter {

	private static final List<String> SUBS = List.of("leave", "challenge", "accept", "decline", "leaderboard");

	private final GameModeRegistry gameModeRegistry;
	private final ChallengeManager challengeManager;
	private NetworkPlayerCache networkPlayerCache;

	public BattleTabCompleter(GameModeRegistry gameModeRegistry, ChallengeManager challengeManager) {
		this.gameModeRegistry = gameModeRegistry;
		this.challengeManager = challengeManager;
	}

	public void setNetworkPlayerCache(NetworkPlayerCache cache) {
		this.networkPlayerCache = cache;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command,
			String alias, String[] args) {
		if (args.length == 1) {
			return filter(SUBS, args[0]);
		}

		String sub = args[0].toLowerCase();

		if ("challenge".equals(sub)) {
			if (args.length == 2) {
				String senderName = sender instanceof Player p ? p.getName() : "";
				Set<String> names = new HashSet<>();

				for (Player online : Bukkit.getOnlinePlayers()) {
					if (!online.getName().equalsIgnoreCase(senderName)) {
						names.add(online.getName());
					}
				}

				if (networkPlayerCache != null && networkPlayerCache.isPopulated()) {
					for (String name : networkPlayerCache.allNames()) {
						if (!name.equalsIgnoreCase(senderName)) {
							names.add(name);
						}
					}
				}

				return filter(new ArrayList<>(names), args[1]);
			}
			if (args.length == 3 && gameModeRegistry != null) {
				List<String> modeIds = gameModeRegistry.allModes().stream()
						.map(m -> m.id())
						.toList();
				return filter(modeIds, args[2]);
			}
		}

		if (("leaderboard".equals(sub) || "lb".equals(sub) || "top".equals(sub)) && args.length == 2) {
			if (gameModeRegistry != null) {
				List<String> modes = new ArrayList<>();
				modes.add("overall");
				gameModeRegistry.allModes().stream().map(m -> m.id()).forEach(modes::add);
				return filter(modes, args[1]);
			}
		}

		if (("accept".equals(sub) || "decline".equals(sub)) && args.length == 2) {
			if (challengeManager != null && sender instanceof Player player) {
				List<String> ids = challengeManager.pendingChallengeIdsFor(player.getUniqueId())
						.stream()
						.map(UUID::toString)
						.toList();
				return filter(ids, args[1]);
			}
		}

		return List.of();
	}

	private static List<String> filter(List<String> options, String prefix) {
		String lower = prefix.toLowerCase();
		return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
	}
}
