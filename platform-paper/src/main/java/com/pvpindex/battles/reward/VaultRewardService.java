package com.pvpindex.battles.reward;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.battle.type.ParticipantResult;
import com.pvpindex.battles.event.PvPIndexBattleFinishEvent;
import com.pvpindex.battles.util.MessageService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pays battle winners via the Vault economy API. Loads reward amounts
 * from {@code vault-rewards.yml} and optionally scales payouts by
 * the player's current win streak.
 */
public final class VaultRewardService implements Listener {

	private final JavaPlugin plugin;
	private final Logger logger;
	private final MessageService messageService;
	private Economy economy;
	private VaultRewardConfig config;

	private final Map<UUID, Integer> sessionStreaks = new ConcurrentHashMap<>();
	private final Map<UUID, Double> lastReward = new ConcurrentHashMap<>();

	public VaultRewardService(JavaPlugin plugin, MessageService messageService) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		this.messageService = messageService;
	}

	/**
	 * Attempts to hook into Vault's economy provider. Returns {@code true}
	 * if an economy implementation is available and rewards should be active.
	 */
	public boolean initialise() {
		if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
			logger.info("Vault not found. Economy rewards are disabled.");
			return false;
		}
		RegisteredServiceProvider<Economy> rsp =
				Bukkit.getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			logger.warning("Vault is installed but no economy provider was registered. Economy rewards are disabled.");
			return false;
		}
		economy = rsp.getProvider();
		reloadConfig();
		if (!config.enabled()) {
			logger.info("Vault economy rewards are disabled in vault-rewards.yml.");
			return false;
		}
		logger.info("Vault economy rewards enabled (" + config.rewards().size() + " modes configured).");
		return true;
	}

	public void reloadConfig() {
		saveDefaultConfig();
		File file = new File(plugin.getDataFolder(), "vault-rewards.yml");
		config = VaultRewardConfig.load(YamlConfiguration.loadConfiguration(file));
	}

	private void saveDefaultConfig() {
		File file = new File(plugin.getDataFolder(), "vault-rewards.yml");
		if (!file.exists()) {
			try (InputStream in = plugin.getResource("vault-rewards.yml")) {
				if (in != null) {
					Files.createDirectories(file.getParentFile().toPath());
					Files.copy(in, file.toPath());
				}
			} catch (IOException e) {
				logger.warning("Failed to save default vault-rewards.yml: " + e.getMessage());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBattleFinish(PvPIndexBattleFinishEvent event) {
		if (economy == null || config == null || !config.enabled()) {
			return;
		}

		BattleSession session = event.getSession();
		GameModeType mode = session.getGameMode();
		if (mode == null) {
			return;
		}

		double baseReward = config.baseReward(mode);
		if (baseReward <= 0) {
			return;
		}

		for (BattleParticipant participant : session.getParticipants()) {
			if (participant.getResult() == ParticipantResult.WIN) {
				handleWin(participant.getUuid(), baseReward, mode);
			} else if (participant.getResult() == ParticipantResult.LOSS
					|| participant.getResult() == ParticipantResult.LEFT) {
				sessionStreaks.put(participant.getUuid(), 0);
			}
		}
	}

	private void handleWin(UUID playerUuid, double baseReward, GameModeType mode) {
		int streak = sessionStreaks.merge(playerUuid, 1, Integer::sum);
		double multiplier = config.calculateMultiplier(streak);
		double payout = Math.round(baseReward * multiplier * 100.0) / 100.0;

		Player player = Bukkit.getPlayer(playerUuid);
		if (player == null || !player.isOnline()) {
			return;
		}

		economy.depositPlayer(player, payout);
		lastReward.put(playerUuid, payout);

		if (multiplier > 1.0) {
			messageService.send(player, "reward.received_streak",
					"%amount%", String.format("%.2f", payout),
					"%mode%", mode.name().toLowerCase(),
					"%multiplier%", String.format("%.1fx", multiplier),
					"%streak%", String.valueOf(streak));
		} else {
			messageService.send(player, "reward.received",
					"%amount%", String.format("%.2f", payout),
					"%mode%", mode.name().toLowerCase());
		}
	}

	public int getStreak(UUID playerUuid) {
		return sessionStreaks.getOrDefault(playerUuid, 0);
	}

	public double getLastReward(UUID playerUuid) {
		return lastReward.getOrDefault(playerUuid, 0.0);
	}

	public boolean isActive() {
		return economy != null && config != null && config.enabled();
	}
}
