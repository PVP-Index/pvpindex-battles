package com.pvpindex.battles.listener;

import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.command.BattleGuiCommand;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gui.GUIBuilder;
import com.pvpindex.battles.gui.GuiConfig;
import com.pvpindex.battles.gui.PlayerGuiState;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.util.MessageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

/**
 * Intercepts clicks inside the "/battle" queue GUI opened by
 * {@link BattleGuiCommand}. Uses {@link PlayerGuiState} to determine
 * whether a mode click joins a queue or sends a challenge.
 *
 * <p>Also handles the SMP risk confirmation GUI — when a player selects
 * a game mode with {@code use_player_inventory=true}, a confirmation
 * dialog is shown before joining the queue.</p>
 */
public class BattleGuiListener implements Listener {

	private final GameModeRegistry gameModeRegistry;
	private final BattleQueueService queueService;
	private final BattleGuiCommand guiCommand;
	private final GuiConfig guiConfig;
	private final MessageService messageService;
	private ChallengeManager challengeManager;

	public BattleGuiListener(GameModeRegistry gameModeRegistry,
			BattleQueueService queueService,
			BattleGuiCommand guiCommand,
			GuiConfig guiConfig,
			MessageService messageService) {
		this.gameModeRegistry = gameModeRegistry;
		this.queueService = queueService;
		this.guiCommand = guiCommand;
		this.guiConfig = guiConfig;
		this.messageService = messageService;
	}

	public void setChallengeManager(ChallengeManager challengeManager) {
		this.challengeManager = challengeManager;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;

		if (guiConfig.confirmationTitle().equals(event.getView().getTitle())) {
			handleConfirmationClick(event, player);
			return;
		}

		if (!guiConfig.battleTitle().equals(event.getView().getTitle())) return;

		event.setCancelled(true);

		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() == Material.AIR) return;

		Material type = clicked.getType();

		if (type == guiConfig.closeMaterial() || type == guiConfig.leaveMaterial()) {
			String name = plainName(clicked);
			String closeName = ChatColor.stripColor(guiConfig.closeName());
			if (closeName.equals(name)) {
				player.closeInventory();
			} else {
				queueService.leave(player);
				player.closeInventory();
			}
			return;
		}

		ItemMeta meta = clicked.getItemMeta();
		if (meta == null) return;

		NamespacedKey modeKey = guiCommand.modeKey();
		String modeId = meta.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);

		if (modeId != null) {
			Optional<GameModeDefinition> modeOpt = gameModeRegistry.findMode(modeId);
			if (modeOpt.isEmpty()) return;
			GameModeDefinition mode = modeOpt.get();

			PlayerGuiState state = guiCommand.guiStates().get(player.getUniqueId());

			if (state != null && state.mode() == PlayerGuiState.Mode.CHALLENGE
					&& challengeManager != null && state.challengeTarget() != null) {
				if (mode.rules() != null && mode.rules().usePlayerInventory()) {
					state.setPendingConfirmationModeId(modeId);
					state.setPendingChallengeTarget(state.challengeTarget());
					player.closeInventory();
					openConfirmationGui(player);
					return;
				}
				player.closeInventory();
				challengeManager.sendChallenge(player, state.challengeTarget(), modeId);
				return;
			}

			if (mode.rules() != null && mode.rules().usePlayerInventory()) {
				PlayerGuiState guiState = guiCommand.guiStates()
						.computeIfAbsent(player.getUniqueId(),
								k -> new PlayerGuiState(PlayerGuiState.Mode.QUEUE, null, 0));
				guiState.setPendingConfirmationModeId(modeId);
				guiState.setPendingChallengeTarget(null);
				player.closeInventory();
				openConfirmationGui(player);
				return;
			}

			player.closeInventory();
			queueService.join(player, mode);
			return;
		}

		String name = plainName(clicked);
		if (name != null) {
			String queueLabel = ChatColor.stripColor(guiConfig.queueTabActive());
			String challengeLabel = ChatColor.stripColor(guiConfig.challengeTabActive());
			String queueInactiveLabel = ChatColor.stripColor(guiConfig.queueTabInactive());
			String challengeInactiveLabel = ChatColor.stripColor(guiConfig.challengeTabInactive());

			if (name.equals(queueLabel) || name.equals(queueInactiveLabel)) {
				guiCommand.guiStates().put(player.getUniqueId(),
						new PlayerGuiState(PlayerGuiState.Mode.QUEUE, null, 0));
			} else if (name.equals(challengeLabel) || name.equals(challengeInactiveLabel)) {
				messageService.send(player, "challenge.gui_hint");
			}
		}
	}

	private void handleConfirmationClick(InventoryClickEvent event, Player player) {
		event.setCancelled(true);

		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() == Material.AIR) return;

		int slot = event.getRawSlot();

		if (slot == guiConfig.confirmYesSlot()) {
			player.closeInventory();
			PlayerGuiState state = guiCommand.guiStates().get(player.getUniqueId());
			if (state != null && state.pendingConfirmationModeId() != null) {
				String modeId = state.pendingConfirmationModeId();
				String challengeTarget = state.pendingChallengeTarget();
				state.setPendingConfirmationModeId(null);
				state.setPendingChallengeTarget(null);
				if (challengeTarget != null && challengeManager != null) {
					challengeManager.sendChallenge(player, challengeTarget, modeId);
				} else {
					gameModeRegistry.findMode(modeId).ifPresent(mode -> queueService.join(player, mode));
				}
			}
		} else if (slot == guiConfig.confirmCancelSlot()) {
			player.closeInventory();
			PlayerGuiState state = guiCommand.guiStates().get(player.getUniqueId());
			if (state != null) {
				state.setPendingConfirmationModeId(null);
				state.setPendingChallengeTarget(null);
			}
		}
	}

	private void openConfirmationGui(Player player) {
		Inventory inv = Bukkit.createInventory(null, guiConfig.confirmationSize(), guiConfig.confirmationTitle());

		// Fill borders with glass panes for visual clarity.
		ItemStack filler = GUIBuilder.glassPane(Material.GRAY_STAINED_GLASS_PANE);
		for (int i = 0; i < inv.getSize(); i++) {
			inv.setItem(i, filler);
		}

		// Info item
		ItemStack infoItem = new ItemStack(guiConfig.confirmInfoMaterial());
		ItemMeta infoMeta = infoItem.getItemMeta();
		if (infoMeta != null) {
			infoMeta.setDisplayName(guiConfig.confirmInfoName());
			infoMeta.setLore(guiConfig.confirmInfoLore());
			infoItem.setItemMeta(infoMeta);
		}
		inv.setItem(guiConfig.confirmInfoSlot(), infoItem);

		// Confirm item
		ItemStack yesItem = new ItemStack(guiConfig.confirmYesMaterial());
		ItemMeta yesMeta = yesItem.getItemMeta();
		if (yesMeta != null) {
			yesMeta.setDisplayName(guiConfig.confirmYesName());
			yesMeta.setLore(guiConfig.confirmYesLore());
			yesItem.setItemMeta(yesMeta);
		}
		inv.setItem(guiConfig.confirmYesSlot(), yesItem);

		// Cancel item
		ItemStack cancelItem = new ItemStack(guiConfig.confirmCancelMaterial());
		ItemMeta cancelMeta = cancelItem.getItemMeta();
		if (cancelMeta != null) {
			cancelMeta.setDisplayName(guiConfig.confirmCancelName());
			cancelMeta.setLore(guiConfig.confirmCancelLore());
			cancelItem.setItemMeta(cancelMeta);
		}
		inv.setItem(guiConfig.confirmCancelSlot(), cancelItem);

		player.openInventory(inv);
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		if (guiConfig.battleTitle().equals(event.getView().getTitle())) {
			PlayerGuiState state = guiCommand.guiStates().get(player.getUniqueId());
			if (state != null && state.pendingConfirmationModeId() != null) {
				return;
			}
			guiCommand.guiStates().remove(player.getUniqueId());
		}
		if (guiConfig.confirmationTitle().equals(event.getView().getTitle())) {
			guiCommand.guiStates().remove(player.getUniqueId());
		}
	}

	private static String plainName(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta == null || !meta.hasDisplayName()) return null;
		return ChatColor.stripColor(meta.getDisplayName());
	}
}
