package com.pvpindex.battles.gui;

import com.pvpindex.battles.data.DataService;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.util.MessageService;
import com.pvpindex.database.model.PlayerStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paginated leaderboard GUI showing player skulls ranked by Elo.
 * Uses the local database ({@link DataService}) as the data source.
 */
public final class LeaderboardGui {

	public static final String TITLE_PREFIX = "\u00a78\u2694 \u00a7fLeaderboard";
	private static final int ROWS = 6;
	private static final int SIZE = ROWS * 9;
	private static final int SLOTS_PER_PAGE = 28;
	private static final int SLOT_START = 10;
	private static final int CLOSE_SLOT = 49;
	private static final int PREV_SLOT = 45;
	private static final int NEXT_SLOT = 53;
	private static final int INFO_SLOT = 4;

	private final JavaPlugin plugin;
	private final DataService dataService;
	private final GameModeRegistry gameModeRegistry;
	private final MessageService messageService;
	private final NamespacedKey pageKey;
	private final NamespacedKey modeKey;

	private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

	public LeaderboardGui(JavaPlugin plugin, DataService dataService,
			GameModeRegistry gameModeRegistry, MessageService messageService) {
		this.plugin = plugin;
		this.dataService = dataService;
		this.gameModeRegistry = gameModeRegistry;
		this.messageService = messageService;
		this.pageKey = new NamespacedKey(plugin, "lb_page");
		this.modeKey = new NamespacedKey(plugin, "lb_mode");
	}

	public Map<UUID, Session> sessions() { return sessions; }

	public void open(Player player, String modeId) {
		if (dataService == null || !dataService.isActive()) {
			messageService.send(player, "leaderboard.no_data");
			return;
		}

		String resolvedMode = modeId != null ? modeId.toLowerCase() : "overall";
		if (!"overall".equals(resolvedMode) && gameModeRegistry.findMode(resolvedMode).isEmpty()) {
			messageService.send(player, "leaderboard.unknown_mode", "%mode%", resolvedMode);
			return;
		}

		sessions.put(player.getUniqueId(), new Session(resolvedMode, 0));
		loadPage(player, resolvedMode, 0);
	}

	public void loadPage(Player player, String modeId, int page) {
		int offset = page * SLOTS_PER_PAGE;
		int fetchLimit = SLOTS_PER_PAGE + 1;

		dataService.provider().statsRepository()
				.getLeaderboard(modeId, "elo", fetchLimit + offset)
				.thenAccept(allStats -> {
					List<PlayerStats> pageStats = allStats.size() > offset
							? allStats.subList(offset, Math.min(allStats.size(), offset + SLOTS_PER_PAGE))
							: List.of();
					boolean hasNextPage = allStats.size() > offset + SLOTS_PER_PAGE;

					Bukkit.getScheduler().runTask(plugin, () -> {
						String displayMode = gameModeRegistry.findMode(modeId)
								.map(m -> m.displayName())
								.orElse(capitalise(modeId));
						String title = TITLE_PREFIX + " \u00a77- \u00a7e" + displayMode;
						Inventory inv = Bukkit.createInventory(null, SIZE, title);

						ItemStack filler = GUIBuilder.glassPane(Material.BLACK_STAINED_GLASS_PANE);
						for (int i = 0; i < SIZE; i++) {
							inv.setItem(i, filler);
						}

						buildInfoItem(inv, displayMode, page, pageStats.size(), offset);
						buildEntries(inv, pageStats, offset);
						buildNavigation(inv, page, hasNextPage, modeId);
						buildCloseItem(inv);

						sessions.put(player.getUniqueId(), new Session(modeId, page));
						player.openInventory(inv);
					});
				})
				.exceptionally(ex -> {
					Bukkit.getScheduler().runTask(plugin, () ->
							messageService.send(player, "leaderboard.error"));
					return null;
				});
	}

	private void buildInfoItem(Inventory inv, String displayMode, int page,
			int entryCount, int offset) {
		ItemStack item = new ItemStack(Material.NETHER_STAR);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + displayMode + " Leaderboard");
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.GRAY + "Page " + (page + 1));
			if (entryCount > 0) {
				lore.add(ChatColor.GRAY + "Showing #" + (offset + 1) + " - #" + (offset + entryCount));
			} else {
				lore.add(ChatColor.GRAY + "No entries found.");
			}
			meta.setLore(lore);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		inv.setItem(INFO_SLOT, item);
	}

	private void buildEntries(Inventory inv, List<PlayerStats> entries, int offset) {
		int[] slots = computeSlots();
		for (int i = 0; i < entries.size() && i < slots.length; i++) {
			PlayerStats stats = entries.get(i);
			int rank = offset + i + 1;
			ItemStack skull = buildPlayerSkull(stats, rank);
			inv.setItem(slots[i], skull);
		}
	}

	private ItemStack buildPlayerSkull(PlayerStats stats, int rank) {
		ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) skull.getItemMeta();
		if (meta != null) {
			String playerName = resolvePlayerName(stats.uuid());
			meta.setOwningPlayer(Bukkit.getOfflinePlayer(stats.uuid()));

			ChatColor rankColour = rank <= 3 ? ChatColor.GOLD : ChatColor.YELLOW;
			meta.setDisplayName(rankColour + "#" + rank + " " + ChatColor.WHITE + playerName);

			List<String> lore = new ArrayList<>();
			lore.add("");
			lore.add(ChatColor.GRAY + "Elo: " + ChatColor.AQUA + stats.elo());
			lore.add(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + stats.wins());
			lore.add(ChatColor.GRAY + "Losses: " + ChatColor.RED + stats.losses());
			if (stats.deaths() > 0) {
				double kd = (double) stats.kills() / stats.deaths();
				lore.add(ChatColor.GRAY + "K/D: " + ChatColor.WHITE + String.format("%.2f", kd));
			} else {
				lore.add(ChatColor.GRAY + "K/D: " + ChatColor.WHITE + stats.kills() + ".00");
			}
			lore.add(ChatColor.GRAY + "Streak: " + ChatColor.WHITE + stats.streak());
			lore.add(ChatColor.GRAY + "Best Streak: " + ChatColor.WHITE + stats.bestStreak());
			lore.add("");
			lore.add(ChatColor.DARK_GRAY + "Mode: " + stats.modeId());
			meta.setLore(lore);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			skull.setItemMeta(meta);
		}
		return skull;
	}

	private void buildNavigation(Inventory inv, int page, boolean hasNext, String modeId) {
		if (page > 0) {
			ItemStack prev = new ItemStack(Material.ARROW);
			ItemMeta meta = prev.getItemMeta();
			if (meta != null) {
				meta.setDisplayName(ChatColor.GREEN + "\u00ab Previous Page");
				meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page - 1);
				meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, modeId);
				prev.setItemMeta(meta);
			}
			inv.setItem(PREV_SLOT, prev);
		}

		if (hasNext) {
			ItemStack next = new ItemStack(Material.ARROW);
			ItemMeta meta = next.getItemMeta();
			if (meta != null) {
				meta.setDisplayName(ChatColor.GREEN + "Next Page \u00bb");
				meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page + 1);
				meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, modeId);
				next.setItemMeta(meta);
			}
			inv.setItem(NEXT_SLOT, next);
		}
	}

	private void buildCloseItem(Inventory inv) {
		ItemStack item = new ItemStack(Material.BARRIER);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(ChatColor.DARK_GRAY + "Close");
			item.setItemMeta(meta);
		}
		inv.setItem(CLOSE_SLOT, item);
	}

	private int[] computeSlots() {
		List<Integer> slots = new ArrayList<>();
		for (int row = 1; row <= 4; row++) {
			int rowStart = row * 9;
			for (int col = 1; col <= 7; col++) {
				slots.add(rowStart + col);
			}
		}
		return slots.stream().mapToInt(Integer::intValue).toArray();
	}

	private String resolvePlayerName(UUID uuid) {
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) return online.getName();
		return Bukkit.getOfflinePlayer(uuid).getName();
	}

	private static String capitalise(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public NamespacedKey pageKey() { return pageKey; }
	public NamespacedKey modeKey() { return modeKey; }

	public record Session(String modeId, int page) {}
}
