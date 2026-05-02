package com.pvpindex.battles.command;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gui.GuiConfig;
import com.pvpindex.battles.gui.PlayerGuiState;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.util.MessageService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the {@code /battle} command tree:
 * <ul>
 *   <li>{@code /battle} — opens queue GUI</li>
 *   <li>{@code /battle leave} — leave queue or forfeit</li>
 *   <li>{@code /battle challenge <player> [mode]} — send a challenge</li>
 *   <li>{@code /battle accept <id>} — accept challenge</li>
 *   <li>{@code /battle decline <id>} — decline challenge</li>
 * </ul>
 */
public class BattleGuiCommand implements CommandExecutor {

	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

	private final JavaPlugin plugin;
	private final GameModeRegistry gameModeRegistry;
	private final BattleQueueService queueService;
	private final BattleService battleService;
	private final GuiConfig guiConfig;
	private final MessageService messageService;
	private ChallengeManager challengeManager;

	private final Map<UUID, PlayerGuiState> guiStates = new ConcurrentHashMap<>();
	private final NamespacedKey modeKey;

	public BattleGuiCommand(JavaPlugin plugin, GameModeRegistry gameModeRegistry,
			BattleQueueService queueService, BattleService battleService,
			GuiConfig guiConfig, MessageService messageService) {
		this.plugin = plugin;
		this.gameModeRegistry = gameModeRegistry;
		this.queueService = queueService;
		this.battleService = battleService;
		this.guiConfig = guiConfig;
		this.messageService = messageService;
		this.modeKey = new NamespacedKey(plugin, "mode_id");
	}

	public void setChallengeManager(ChallengeManager challengeManager) {
		this.challengeManager = challengeManager;
	}

	public Map<UUID, PlayerGuiState> guiStates() { return guiStates; }
	public NamespacedKey modeKey() { return modeKey; }
	public GuiConfig guiConfig() { return guiConfig; }

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			messageService.send(sender, "general.player_only");
			return true;
		}

		if (args.length == 0) {
			if (queueService.isQueued(player.getUniqueId())) {
				messageService.send(player, "queue.already_queued");
				return true;
			}
			openGui(player, PlayerGuiState.Mode.QUEUE, null);
			return true;
		}

		return switch (args[0].toLowerCase()) {
			case "leave"     -> handleLeave(player);
			case "challenge" -> handleChallenge(player, args);
			case "accept"    -> handleAccept(player, args);
			case "decline"   -> handleDecline(player, args);
			default          -> false;
		};
	}

	// ── Subcommand handlers ─────────────────────────────────────────────────

	private boolean handleLeave(Player player) {
		if (queueService.leave(player)) return true;

		if (battleService != null) {
			var session = battleService.findActiveBattleFor(player.getUniqueId());
			if (session.isPresent()) {
				BattleSession s = session.get();
				UUID quitter = player.getUniqueId();
				List<UUID> winners = new ArrayList<>();
				s.getParticipants().forEach(p -> {
					if (!p.getUuid().equals(quitter)) winners.add(p.getUuid());
				});
				battleService.markPlayerLeftEarly(s.getUuid(), quitter);
				battleService.endAndCleanup(s.getUuid(), winners);
				return true;
			}
		}

		messageService.send(player, "queue.not_in_queue");
		return true;
	}

	private boolean handleChallenge(Player player, String[] args) {
		if (challengeManager == null) {
			messageService.send(player, "challenge.not_available");
			return true;
		}
		if (args.length < 2) {
			messageService.send(player, "challenge.usage_challenge");
			return true;
		}
		String targetName = args[1];
		if (args.length >= 3) {
			String modeId = args[2].toLowerCase();
			if (gameModeRegistry.findMode(modeId).isEmpty()) {
				messageService.send(player, "challenge.unknown_mode", "%mode%", modeId);
				return true;
			}
			challengeManager.sendChallenge(player, targetName, modeId);
		} else {
			openGui(player, PlayerGuiState.Mode.CHALLENGE, targetName);
		}
		return true;
	}

	private boolean handleAccept(Player player, String[] args) {
		if (challengeManager == null) return true;
		if (args.length < 2) {
			messageService.send(player, "challenge.usage_accept");
			return true;
		}
		try {
			UUID challengeId = UUID.fromString(args[1]);
			challengeManager.acceptChallenge(player, challengeId);
		} catch (IllegalArgumentException e) {
			messageService.send(player, "challenge.invalid_id");
		}
		return true;
	}

	private boolean handleDecline(Player player, String[] args) {
		if (challengeManager == null) return true;
		if (args.length < 2) {
			messageService.send(player, "challenge.usage_decline");
			return true;
		}
		try {
			UUID challengeId = UUID.fromString(args[1]);
			challengeManager.declineChallenge(player, challengeId);
		} catch (IllegalArgumentException e) {
			messageService.send(player, "challenge.invalid_id");
		}
		return true;
	}

	// ── GUI construction ────────────────────────────────────────────────────

	private void openGui(Player player, PlayerGuiState.Mode mode, String challengeTarget) {
		guiStates.put(player.getUniqueId(), new PlayerGuiState(mode, challengeTarget, 0));
		Inventory inv = Bukkit.createInventory(null, guiConfig.battleSize(), guiConfig.battleTitle());

		inv.setItem(guiConfig.titleSlot(), buildTitleItem(mode));

		Collection<GameModeDefinition> modes = gameModeRegistry.allModes();
		int slot = guiConfig.modeSlotStart();
		for (GameModeDefinition gm : modes) {
			if (slot > guiConfig.modeSlotEnd()) break;
			inv.setItem(slot++, buildModeItem(gm));
		}

		buildCategoryRow(inv, mode);

		inv.setItem(guiConfig.leaveSlot(), buildLeaveItem());
		inv.setItem(guiConfig.closeSlot(), buildCloseItem());

		player.openInventory(inv);
	}

	private ItemStack buildTitleItem(PlayerGuiState.Mode mode) {
		ItemStack item = new ItemStack(guiConfig.titleMaterial());
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			Component name = mode == PlayerGuiState.Mode.CHALLENGE
					? guiConfig.titleChallengeName()
					: guiConfig.titleQueueName();
			meta.displayName(name);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		return item;
	}

	ItemStack buildModeItem(GameModeDefinition mode) {
		ItemStack item = new ItemStack(guiConfig.iconFor(mode.id()));
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(GuiConfig.parseColour(guiConfig.modeNamePrefix() + mode.displayName()));

			List<Component> lore = new ArrayList<>();
			for (String line : mode.description()) {
				lore.add(LEGACY.deserialize(line));
			}
			if (!mode.description().isEmpty()) {
				lore.add(Component.empty());
			}
			String countLine = guiConfig.queueCountLine()
					.replace("%count%", String.valueOf(queueService.queueSize(mode.id())));
			lore.add(GuiConfig.parseColour(countLine));
			lore.add(Component.empty());
			lore.add(GuiConfig.parseColour(guiConfig.clickLine()));

			meta.lore(lore);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.id());
			item.setItemMeta(meta);
		}
		return item;
	}

	private void buildCategoryRow(Inventory inv, PlayerGuiState.Mode activeMode) {
		boolean queueActive = activeMode == PlayerGuiState.Mode.QUEUE;

		ItemStack queueTab = buildTab(guiConfig.queueTabMaterial(),
				queueActive ? guiConfig.queueTabActive() : guiConfig.queueTabInactive(),
				queueActive);
		ItemStack challengeTab = buildTab(guiConfig.challengeTabMaterial(),
				!queueActive ? guiConfig.challengeTabActive() : guiConfig.challengeTabInactive(),
				!queueActive);

		ItemStack activeBattles = new ItemStack(guiConfig.activeBattlesMaterial());
		ItemMeta abMeta = activeBattles.getItemMeta();
		if (abMeta != null) {
			int count = battleService != null ? battleService.activeBattles().size() : 0;
			abMeta.displayName(guiConfig.activeBattlesName());
			String loreLine = guiConfig.activeBattlesLoreFormat()
					.replace("%count%", String.valueOf(count));
			abMeta.lore(List.of(GuiConfig.parseColour(loreLine)));
			abMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			activeBattles.setItemMeta(abMeta);
		}

		inv.setItem(guiConfig.queueTabSlot(), queueTab);
		inv.setItem(guiConfig.challengeTabSlot(), challengeTab);
		inv.setItem(guiConfig.activeBattlesSlot(), activeBattles);
	}

	private static ItemStack buildTab(Material material, Component name, boolean active) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(name);
			if (active) {
				meta.lore(List.of(Component.text("Currently viewing", NamedTextColor.GREEN)));
				meta.setEnchantmentGlintOverride(true);
			}
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
			item.setItemMeta(meta);
		}
		return item;
	}

	private ItemStack buildLeaveItem() {
		ItemStack item = new ItemStack(guiConfig.leaveMaterial());
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(guiConfig.leaveName());
			meta.lore(guiConfig.leaveLore());
			item.setItemMeta(meta);
		}
		return item;
	}

	private ItemStack buildCloseItem() {
		ItemStack item = new ItemStack(guiConfig.closeMaterial());
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(guiConfig.closeName());
			item.setItemMeta(meta);
		}
		return item;
	}
}
