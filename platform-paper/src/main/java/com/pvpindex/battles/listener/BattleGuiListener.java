package com.pvpindex.battles.listener;

import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.command.BattleGuiCommand;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gui.GuiConfig;
import com.pvpindex.battles.gui.PlayerGuiState;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.util.MessageService;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Intercepts clicks inside the "/battle" queue GUI opened by
 * {@link BattleGuiCommand}. Uses {@link PlayerGuiState} to determine
 * whether a mode click joins a queue or sends a challenge.
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
		if (!guiConfig.battleTitle().equals(event.getView().title())) return;

		event.setCancelled(true);

		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() == Material.AIR) return;

		Material type = clicked.getType();

		if (type == guiConfig.closeMaterial() || type == guiConfig.leaveMaterial()) {
			String name = plainName(clicked);
			String closeName = PlainTextComponentSerializer.plainText().serialize(guiConfig.closeName());
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

			PlayerGuiState state = guiCommand.guiStates().get(player.getUniqueId());
			player.closeInventory();

			if (state != null && state.mode() == PlayerGuiState.Mode.CHALLENGE
					&& challengeManager != null && state.challengeTarget() != null) {
				challengeManager.sendChallenge(player, state.challengeTarget(), modeId);
			} else {
				queueService.join(player, modeOpt.get());
			}
			return;
		}

		String name = plainName(clicked);
		if (name != null) {
			String queueLabel = PlainTextComponentSerializer.plainText().serialize(guiConfig.queueTabActive());
			String challengeLabel = PlainTextComponentSerializer.plainText().serialize(guiConfig.challengeTabActive());
			String queueInactiveLabel = PlainTextComponentSerializer.plainText().serialize(guiConfig.queueTabInactive());
			String challengeInactiveLabel = PlainTextComponentSerializer.plainText().serialize(guiConfig.challengeTabInactive());

			if (name.equals(queueLabel) || name.equals(queueInactiveLabel)) {
				guiCommand.guiStates().put(player.getUniqueId(),
						new PlayerGuiState(PlayerGuiState.Mode.QUEUE, null, 0));
			} else if (name.equals(challengeLabel) || name.equals(challengeInactiveLabel)) {
				messageService.send(player, "challenge.gui_hint");
			}
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		if (guiConfig.battleTitle().equals(event.getView().title())) {
			guiCommand.guiStates().remove(player.getUniqueId());
		}
	}

	private static String plainName(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta == null || !meta.hasDisplayName()) return null;
		return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
	}
}
