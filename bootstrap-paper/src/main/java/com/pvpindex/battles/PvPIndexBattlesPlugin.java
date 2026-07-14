package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.api.BattlePayloadFactory;
import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.arena.ArenaManager;
import com.pvpindex.battles.battle.BattleManager;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.PlayerStateService;
import com.pvpindex.battles.challenge.ChallengeArrivalListener;
import com.pvpindex.battles.challenge.ChallengeManager;
import com.pvpindex.battles.command.BattleGuiCommand;
import com.pvpindex.battles.command.BattleTabCompleter;
import com.pvpindex.battles.command.PvPIndexCommand;
import com.pvpindex.battles.command.PvPIndexTabCompleter;
import com.pvpindex.battles.moderation.ModerationTabCompleter;
import com.pvpindex.battles.config.ConfigManager;
import com.pvpindex.battles.gamemode.GameModeDefinition;
import com.pvpindex.battles.gamemode.GameModeLoader;
import com.pvpindex.battles.gamemode.GameModeRegistry;
import com.pvpindex.battles.gamemode.KitApplier;
import com.pvpindex.battles.identifier.WorldIdentifier;
import com.pvpindex.battles.identifier.WorldNormalizer;
import com.pvpindex.battles.battle.BattleBatchScheduler;
import com.pvpindex.battles.gui.LeaderboardGui;
import com.pvpindex.battles.listener.BattleCommandBlockListener;
import com.pvpindex.battles.listener.BattleEventListener;
import com.pvpindex.battles.listener.BattleGuiListener;
import com.pvpindex.battles.listener.ProxyMessageListener;
import com.pvpindex.battles.listener.SetupListener;
import com.pvpindex.battles.listener.StateRestoreListener;
import com.pvpindex.battles.listener.WorldCleanupListener;
import com.pvpindex.battles.messaging.NetworkPlayerCache;
import com.pvpindex.battles.messaging.PaperMessenger;
import com.pvpindex.battles.velocity.VelocityTracker;
import com.pvpindex.battles.moderation.BanService;
import com.pvpindex.battles.moderation.FederatedBanSync;
import com.pvpindex.battles.moderation.ModerationCommand;
import com.pvpindex.battles.moderation.ModerationListener;
import com.pvpindex.battles.moderation.ModerationService;
import com.pvpindex.battles.moderation.ReportService;
import com.pvpindex.battles.placeholder.PlaceholderUpdateListener;
import com.pvpindex.battles.placeholder.PlayerStatCache;
import com.pvpindex.battles.placeholder.PvPIndexExpansion;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.replay.PacketCaptureService;
import com.pvpindex.battles.replay.PacketReplayBridge;
import com.pvpindex.battles.replay.bridge.BukkitReplayBridge;
import com.pvpindex.battles.storage.FileStorageService;
import com.pvpindex.battles.gui.GuiConfig;
import com.pvpindex.battles.util.DebugLogger;
import com.pvpindex.battles.util.MessageService;
import com.pvpindex.battles.world.ArenaPoolService;
import com.pvpindex.battles.world.ProceduralArenaStrategy;
import com.pvpindex.battles.world.ProceduralCrystalArenaStrategy;
import com.pvpindex.battles.world.ProceduralSumoArenaStrategy;
import com.pvpindex.battles.world.SchematicLoader;
import com.pvpindex.battles.world.SchematicStrategy;
import com.pvpindex.battles.world.WorldCopyStrategy;
import com.pvpindex.battles.world.WorldGeneratorService;
import com.pvpindex.battles.teams.TeamsGuardService;
import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

public class PvPIndexBattlesPlugin extends JavaPlugin {
	private ConfigManager configManager;
	private ArenaManager arenaManager;
	private MessageService messageService;
	private BattleService battleService;
	private GameModeRegistry gameModeRegistry;
	private GameModeLoader gameModeLoader;
	private WorldGeneratorService worldGeneratorService;
	private BanService banService;
	private ReportService reportService;
	private ModerationService moderationService;
	private FederatedBanSync federatedBanSync;
	private PacketCaptureService packetCaptureService;
	private PacketReplayBridge replayBridge;
	private BattleQueueService battleQueueService;
	private PvPIndexApiClient apiClient;
	private PlayerStateService playerStateService;
	private ArenaPoolService arenaPoolService;
	private VelocityTracker velocityTracker;
	private BattleBatchScheduler battleBatchScheduler;
	private BattleEventListener battleEventListener;
	private PaperMessenger paperMessenger;
	private ProxyMessageListener proxyMessageListener;
	private com.pvpindex.battles.version.VersionAdapter versionAdapter;
	private WorldNormalizer worldNormalizer;
	private DebugLogger debugLogger;
	private ChallengeManager challengeManager;
	private BattleManager battleManager;
	private GuiConfig guiConfig;
	private NetworkPlayerCache networkPlayerCache;
	private com.pvpindex.battles.battle.SmpLootPhaseService smpLootPhaseService;
	private com.pvpindex.battles.network.LobbyNetworkService lobbyNetworkService;
	private com.pvpindex.battles.data.DataService dataService;
	private com.pvpindex.battles.reward.VaultRewardService vaultRewardService;

	@Override
	public void onEnable() {
		printBanner();

		// Detect Minecraft version and load the correct adapter
		com.pvpindex.battles.version.VersionAdapter versionAdapter = resolveVersionAdapter();
		if (versionAdapter == null) {
			getLogger().severe("Unsupported Minecraft version! PvPIndex supports 1.21.x and 26.1.x Paper servers.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		getLogger().info("Detected version adapter: " + versionAdapter.getClass().getSimpleName());

		saveDefaultConfig();
		saveResourceIfAbsent("arenas.yml");
		saveResourceIfAbsent("gamemodes.yml");
		saveResourceIfAbsent("gui.yml");
		saveResourceIfAbsent("templates.yml");
		saveResourceIfAbsent("schematics.yml");
		// Bundled schematic files. Only written once (never overwrite server-owner edits).
		saveResourceIfAbsent("schematics/arena.schem");
		saveResourceIfAbsent("schematics/colosseum.schem");
		saveResourceIfAbsent("schematics/pvparena.schem");
		saveResourceIfAbsent("schematics/royal.schem");

		configManager = new ConfigManager(this);
		configManager.reload();

		new org.bstats.bukkit.Metrics(this, 31131);

		arenaManager = new ArenaManager(this);
		arenaManager.reload();

		messageService = new MessageService(this);
		messageService.reload();

		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		FileStorageService fileStorageService = new FileStorageService(getDataFolder().toPath(), objectMapper);
		try {
			fileStorageService.initialise();
		} catch (IOException e) {
			getLogger().severe("Failed to initialise storage: " + e.getMessage());
		}

		// Configurable game modes / kits
		gameModeRegistry = new GameModeRegistry();
		gameModeLoader = new GameModeLoader(this, gameModeRegistry);
		gameModeLoader.reload();

		// World generator (copy + schematic strategies registered out of the box)
		Path templatesDir = getDataFolder().toPath().resolve("templates");
		Path runtimeWorldsDir = getServer() == null ? getDataFolder().toPath().resolve("runtime-worlds")
				: getServer().getWorldContainer().toPath();
		worldGeneratorService = new WorldGeneratorService(this);
		worldGeneratorService.register(new WorldCopyStrategy(this, templatesDir, runtimeWorldsDir));
		worldGeneratorService.register(new SchematicStrategy(this, objectMapper, templatesDir));
		worldGeneratorService.register(new ProceduralArenaStrategy(this));
		worldGeneratorService.register(new ProceduralCrystalArenaStrategy(this));
		worldGeneratorService.register(new ProceduralSumoArenaStrategy(this));
		// Schematic spawn metadata, loaded from schematics.yml. Editable by server owners.
		SchematicLoader schematicLoader = new SchematicLoader(this);
		worldGeneratorService.setSchematicLoader(schematicLoader);
		worldGeneratorService.reload(templatesDir);

		// Replay subsystem (snapshot recorder + playback bridge)
		BattleReplayRecorder recorder = new BattleReplayRecorder(this, objectMapper, configManager.settings().replayDetailLevel());
		packetCaptureService = new PacketCaptureService(this, configManager.replaySettings());
		replayBridge = new BukkitReplayBridge(this, messageService);

		// Velocity tracker. Wire into the capture loop if enabled.
		if (configManager.settings().velocityEnabled()) {
			velocityTracker = new VelocityTracker(
					recorder,
					configManager.settings().velocityThreshold(),
					configManager.settings().velocityTrackingIntervalTicks());
			packetCaptureService.setVelocityTracker(velocityTracker);
			getLogger().info("Velocity tracking enabled (threshold="
					+ configManager.settings().velocityThreshold() + ", interval="
					+ configManager.settings().velocityTrackingIntervalTicks() + " ticks).");
		}

		apiClient = new PvPIndexApiClient(configManager.settings(), objectMapper, getLogger());
		battleService = new BattleService(this, configManager.settings(), recorder, apiClient,
				new BattlePayloadFactory(), fileStorageService);
		battleService.setPacketCaptureService(packetCaptureService);

		// Periodic background retry of failed-submissions/. Keeps the queue draining
		// even if the API was offline when the battle finished.
		int retryInterval = configManager.settings().persistentRetryIntervalSeconds();
		if (retryInterval > 0) {
			long ticks = (long) retryInterval * 20L;
			scheduleAsyncRepeating(() -> {
				int pending = battleService.pendingFailedSubmissionCount();
				if (pending > 0) {
					int recovered = battleService.retryFailedSubmissions();
					if (recovered > 0) {
						getLogger().info("Background retry: " + recovered + "/" + pending
								+ " persisted battle submission(s) accepted.");
					}
				}
			}, ticks, ticks);
			getLogger().info("Persistent submission retry scheduled every " + retryInterval + "s.");
		}

		// On startup, sync any local battles that were saved to disk before a
		// crash / force-kill and never confirmed as received by the API.
		// Runs 30 s after enable to give the server time to fully start up and
		// establish a stable API connection before hammering it with old data.
		scheduleAsyncDelayed(() -> {
			int queued = battleService.syncUnsubmittedBattles(true);
			if (queued == 0) {
				getLogger().info("Local battle sync: all battles already submitted, nothing to do.");
			}
		}, 30 * 20L);

		// Moderation
		banService = new BanService(objectMapper, getDataFolder().toPath());
		reportService = new ReportService(objectMapper, getDataFolder().toPath());
		try {
			banService.load();
			reportService.load();
		} catch (IOException e) {
			getLogger().warning("Failed to load moderation data: " + e.getMessage());
		}
		moderationService = new ModerationService(this, battleService, banService, reportService,
				configManager.moderationSettings(), replayBridge);
		federatedBanSync = new FederatedBanSync(this, banService, apiClient,
				configManager.moderationSettings(), configManager.settings());
		federatedBanSync.start();

		// Battle heartbeat batch scheduler. Fire-and-forget async POSTs.
		if (configManager.settings().battleBatchEnabled()) {
			battleBatchScheduler = new BattleBatchScheduler(
					this, battleService, apiClient,
					configManager.settings().battleBatchMaxSize(),
					configManager.settings().debug());
			battleBatchScheduler.start(configManager.settings().battleBatchFlushIntervalTicks());
			getLogger().info("Battle heartbeat scheduler started (interval="
					+ configManager.settings().battleBatchFlushIntervalTicks() + " ticks).");
		}

		// Listeners
		battleEventListener = new BattleEventListener(battleService, recorder, gameModeRegistry);
		for (org.bukkit.event.Listener listener : battleEventListener.getDelegates()) {
			getServer().getPluginManager().registerEvents(listener, this);
		}
		getServer().getPluginManager().registerEvents(new ModerationListener(moderationService, messageService), this);
		getServer().getPluginManager().registerEvents(new SetupListener(this, configManager, messageService), this);
		if (configManager.settings().blockCommandsInBattle()) {
			getServer().getPluginManager().registerEvents(
					new BattleCommandBlockListener(battleService, configManager.settings(), messageService), this);
			getLogger().info("Command blocking during battles enabled.");
		}
		getServer().getPluginManager().registerEvents(
				new WorldCleanupListener(this, battleService, velocityTracker), this);

		// Player state save/restore (snapshot before battle, restore on end / on rejoin after crash)
		boolean includeEnderChest = getConfig().getBoolean("player_state.include_ender_chest", true);
		boolean preventBackCommand = configManager.settings().preventBackCommand();
		playerStateService = new PlayerStateService(this, includeEnderChest, versionAdapter, preventBackCommand);
		var afterBattleSettings = configManager.afterBattleLocationSettings();
		var afterBattleLoc = afterBattleSettings.resolveLocation();
		if (afterBattleLoc != null) {
			playerStateService.setAfterBattleLocation(afterBattleLoc);
			getLogger().info("After-battle location set to " + afterBattleSettings.mode()
					+ " (" + afterBattleLoc.getWorld().getName()
					+ " " + afterBattleLoc.getBlockX()
					+ " " + afterBattleLoc.getBlockY()
					+ " " + afterBattleLoc.getBlockZ() + ")");
		}
		battleService.setPlayerStateService(playerStateService);
		getServer().getPluginManager().registerEvents(
				new StateRestoreListener(this, playerStateService, messageService), this);

		// Arena pool. Pre-generates worlds so matchmaking gets an instance instantly.
		arenaPoolService = new ArenaPoolService(this, worldGeneratorService);
		battleService.setArenaPoolService(arenaPoolService);
		// Sweep any orphan pvpindex_* world folders from a prior crash *before* warming.
		arenaPoolService.sweepOrphans();
		boolean poolEnabled = getConfig().getBoolean("arena_pool.enabled", true);
		int warmSize = getConfig().getInt("arena_pool.warm_size_per_template", 2);
		boolean refillAsync = getConfig().getBoolean("arena_pool.refill_async", true);
		arenaPoolService.configure(warmSize, refillAsync);
		if (poolEnabled && warmSize > 0) {
			arenaPoolService.warmAll(warmSize);
		}

		// GUI configuration (gui.yml)
		guiConfig = GuiConfig.load(getDataFolder(), getLogger());

		// WorldNormalizer + DebugLogger
		worldNormalizer = new WorldNormalizer();
		for (GameModeDefinition mode : gameModeRegistry.allModes()) {
			worldNormalizer.register(new WorldIdentifier(mode.id(), mode.displayName()));
		}
		debugLogger = new DebugLogger(getLogger(), configManager.settings().debug());

		// SMP loot phase service. Handles post-death item collection cooldown.
		smpLootPhaseService = new com.pvpindex.battles.battle.SmpLootPhaseService(this, battleService, playerStateService);
		battleEventListener.setSmpLootPhaseService(smpLootPhaseService);

		// Queue service + GUI listener (must be after gameModeRegistry is populated)
		battleQueueService = new BattleQueueService(this, battleService, worldGeneratorService,
				arenaPoolService, playerStateService, gameModeRegistry, new KitApplier(versionAdapter), versionAdapter, messageService);
		// Wire queue service into the event listener so quitting players are dequeued.
		battleEventListener.setQueueService(battleQueueService);

		// BattleManager facade
		battleManager = new BattleManager(battleService, battleQueueService, worldNormalizer);

		// Battle GUI command (needs plugin reference for NamespacedKey)
		BattleGuiCommand battleGuiCommand = new BattleGuiCommand(this, gameModeRegistry, battleQueueService, battleService, guiConfig, messageService);
		BattleGuiListener battleGuiListener = new BattleGuiListener(gameModeRegistry, battleQueueService, battleGuiCommand, guiConfig, messageService);
		getServer().getPluginManager().registerEvents(battleGuiListener, this);

		// Velocity proxy messaging. Only active when proxy.enabled = true.
		if (configManager.settings().proxyEnabled()) {
			paperMessenger = new PaperMessenger(this, objectMapper,
					configManager.settings().proxySecret());
			paperMessenger.register();
			battleService.setPaperMessenger(paperMessenger);

			networkPlayerCache = new NetworkPlayerCache();

			proxyMessageListener = new ProxyMessageListener(this, battleService, objectMapper,
					configManager.settings().proxySecret(), configManager.settings().debug());
			proxyMessageListener.setNetworkPlayerCache(networkPlayerCache);
			proxyMessageListener.register();

			// Proxy heartbeat timer. Lets Velocity know this backend is alive.
			int hbTicks = configManager.settings().proxyHeartbeatIntervalTicks();
			if (hbTicks > 0) {
				scheduleAsyncRepeating(() -> paperMessenger.sendHeartbeat(
						configManager.settings().serverId(),
						battleService.activeBattles().size()), hbTicks, hbTicks);
			}
			getLogger().info("Velocity proxy messaging enabled (channel=pvpindex:proxy).");
		}

		// Optional database service. Only active when database.enabled = true.
		if (configManager.databaseSettings().enabled()) {
			dataService = new com.pvpindex.battles.data.DataService(this, configManager.databaseSettings());
			dataService.start();
		}

		// Lobby network service. Only active when lobby.enabled = true.
		if (configManager.lobbySettings().enabled()) {
			networkPlayerCache = networkPlayerCache != null ? networkPlayerCache : new NetworkPlayerCache();
			lobbyNetworkService = new com.pvpindex.battles.network.LobbyNetworkService(this, configManager.lobbySettings());
			lobbyNetworkService.start();
			if (lobbyNetworkService.isActive()) {
				networkPlayerCache.setLobbyMode(true);
				lobbyNetworkService.playerSync().setCache(networkPlayerCache);
				lobbyNetworkService.playerSync().seedLocalPlayers();
				getLogger().info("Lobby network service active - global sync via Redis enabled.");
			}
		}

		// ChallengeManager. Works in proxy, lobby, and standalone modes.
		challengeManager = new ChallengeManager(this, paperMessenger,
				battleQueueService, gameModeRegistry, debugLogger, guiConfig,
				configManager.settings().proxyEnabled(), messageService);
		if (lobbyNetworkService != null && lobbyNetworkService.isActive()) {
			challengeManager.setLobbyServices(lobbyNetworkService.challengeSync(), networkPlayerCache,
					lobbyNetworkService.transfers(), configManager.lobbySettings().velocityServerName());
		}

		// TeamsAPI guard. Optional, disabled by default.
		TeamsGuardService teamsGuard = new TeamsGuardService(
				configManager.settings().teamsGuardEnabled(), getLogger());
		challengeManager.setTeamsGuard(teamsGuard);
		if (teamsGuard.isEnabled()) {
			getLogger().info("TeamsAPI guard enabled: same-team challenges will be blocked.");
		}
		if (dataService != null && dataService.isActive()) {
			LeaderboardGui leaderboardGui = new LeaderboardGui(this, dataService, gameModeRegistry, messageService);
			battleGuiCommand.setLeaderboardGui(leaderboardGui);
			getLogger().info("Leaderboard GUI enabled (database active).");
		}

		battleGuiCommand.setChallengeManager(challengeManager);
		battleGuiListener.setChallengeManager(challengeManager);
		if (proxyMessageListener != null) {
			proxyMessageListener.setChallengeManager(challengeManager);
		}

		// Arrival listener for cross-server challenge transfers
		ChallengeArrivalListener arrivalListener = new ChallengeArrivalListener(this, challengeManager);
		getServer().getPluginManager().registerEvents(arrivalListener, this);
		challengeManager.setArrivalListener(arrivalListener);

		// Frame capture loop. Only active when there is a battle running.
		packetCaptureService.start(battleService::activeBattles);

		// Periodic stale-data cleanup: evict swing timers + velocity cache for
		// players no longer in any active battle.
		int cleanupTicks = configManager.settings().cleanupIntervalTicks();
		if (cleanupTicks > 0) {
			// Folia does not support sync global tasks; swallow UnsupportedOperationException
			// and skip the cleanup timer on that platform (concurrent structures self-manage).
			try { getServer().getScheduler().runTaskTimer(this, () -> {
				java.util.List<com.pvpindex.battles.battle.BattleSession> active =
						battleService.activeBattles();
				int evicted = 0;
				if (battleEventListener != null) {
					evicted += battleEventListener.evictStalePlayers(active);
				}
				if (velocityTracker != null) {
					java.util.Set<java.util.UUID> activePlayers = active.stream()
							.flatMap(s -> s.getParticipants().stream()
									.map(com.pvpindex.battles.battle.BattleParticipant::getUuid))
							.collect(java.util.stream.Collectors.toSet());
					evicted += velocityTracker.evictInactive(activePlayers);
				}
				if (configManager.settings().debug() && evicted > 0) {
					getLogger().info("[Cleanup] Evicted " + evicted + " stale tracking entries.");
				}
			}, cleanupTicks, cleanupTicks);
			} catch (UnsupportedOperationException ignored) {
				// Folia: fall back to async. Safe since these are ConcurrentHashMap evictions.
				scheduleAsyncRepeating(() -> {
					java.util.List<com.pvpindex.battles.battle.BattleSession> active =
							battleService.activeBattles();
					int evicted = 0;
					if (battleEventListener != null) {
						evicted += battleEventListener.evictStalePlayers(active);
					}
					if (velocityTracker != null) {
						java.util.Set<java.util.UUID> activePlayers = active.stream()
								.flatMap(s -> s.getParticipants().stream()
										.map(com.pvpindex.battles.battle.BattleParticipant::getUuid))
								.collect(java.util.stream.Collectors.toSet());
						evicted += velocityTracker.evictInactive(activePlayers);
					}
					if (configManager.settings().debug() && evicted > 0) {
						getLogger().info("[Cleanup] Evicted " + evicted + " stale tracking entries.");
					}
				}, cleanupTicks, cleanupTicks);
			}
		}

		// Commands
		var cmd = getCommand("pvpindex");
		if (cmd != null) {
			var pvpIndexCmd = new PvPIndexCommand(configManager, messageService, battleService, fileStorageService, apiClient);
			cmd.setExecutor(pvpIndexCmd);
			cmd.setTabCompleter(new PvPIndexTabCompleter(battleService));
		}
		var modCmd = getCommand("pvpmod");
		if (modCmd != null) {
			modCmd.setExecutor(new ModerationCommand(moderationService, battleService, fileStorageService,
					objectMapper, configManager.settings().serverId(), messageService));
			modCmd.setTabCompleter(new ModerationTabCompleter(battleService));
		}
		var battleCmd = getCommand("battle");
		if (battleCmd != null) {
			battleCmd.setExecutor(battleGuiCommand);
			BattleTabCompleter battleTabCompleter = new BattleTabCompleter(gameModeRegistry, challengeManager);
			if (networkPlayerCache != null) {
				battleTabCompleter.setNetworkPlayerCache(networkPlayerCache);
			}
			battleCmd.setTabCompleter(battleTabCompleter);
		}

		// Vault economy rewards - registered only when Vault is present
		vaultRewardService = new com.pvpindex.battles.reward.VaultRewardService(this, messageService);
		boolean vaultEnabled = vaultRewardService.initialise();
		if (vaultEnabled) {
			getServer().getPluginManager().registerEvents(vaultRewardService, this);
		}

		// PlaceholderAPI expansion - registered only when PAPI is present
		boolean papiEnabled = false;
		if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
			PlayerStatCache statCache = new PlayerStatCache(this, apiClient);
			getServer().getPluginManager().registerEvents(new PlaceholderUpdateListener(statCache), this);
			PvPIndexExpansion expansion = new PvPIndexExpansion(statCache, battleService, battleQueueService);
			expansion.setWorldNormalizer(worldNormalizer);
			if (vaultRewardService != null && vaultRewardService.isActive()) {
				expansion.setVaultRewardService(vaultRewardService);
			}
			expansion.register();
			papiEnabled = true;
		}

		printStartupSummary(versionAdapter, papiEnabled, vaultEnabled);
		printSecurityWarnings();
	}

	@Override
	public void onDisable() {
		if (lobbyNetworkService != null) lobbyNetworkService.shutdown();
		if (dataService != null) dataService.shutdown();
		if (smpLootPhaseService != null) smpLootPhaseService.cancelAll();
		if (battleBatchScheduler != null) battleBatchScheduler.stop();
		if (proxyMessageListener != null) proxyMessageListener.unregister();
		if (paperMessenger != null) paperMessenger.unregister();
		if (packetCaptureService != null) packetCaptureService.stop();
		if (velocityTracker != null) velocityTracker.clearAll();
		if (federatedBanSync != null) federatedBanSync.stop();
		// Unload + delete every arena world we created (and orphan-sweep too).
		if (arenaPoolService != null) {
			try { arenaPoolService.shutdown(); }
			catch (RuntimeException e) { getLogger().warning("Arena pool shutdown failed: " + e.getMessage()); }
		}
		getLogger().info("\u001B[31mPVP INDEX \u001B[90mhas been \u001B[31mdisabled\u001B[90m. Goodbye!\u001B[0m");
	}

	public BattleService battleService() { return battleService; }
	public GameModeRegistry gameModeRegistry() { return gameModeRegistry; }
	public WorldGeneratorService worldGeneratorService() { return worldGeneratorService; }
	public ModerationService moderationService() { return moderationService; }
	public PacketCaptureService packetCaptureService() { return packetCaptureService; }

	/** Allow downstream extensions to swap in an NMS / PacketEvents bridge. */
	public void setReplayBridge(PacketReplayBridge bridge) {
		this.replayBridge = bridge;
	}

	private void printBanner() {
		String R = "\u001B[0m";
		String G = "\u001B[32m";
		String Y = "\u001B[33m";
		String RE = "\u001B[31m";
		String GR = "\u001B[90m";
		String W = "\u001B[97m";
		String version = getDescription().getVersion();

		var c = getServer().getConsoleSender();
		c.sendMessage("");
		c.sendMessage(G  + "  ██████╗ ██╗   ██╗██████╗    " + Y  + "██╗███╗   ██╗██████╗ ███████╗██╗  ██╗" + R);
		c.sendMessage(G  + "  ██╔══██╗██║   ██║██╔══██╗   " + Y  + "██║████╗  ██║██╔══██╗██╔════╝╚██╗██╔╝" + R);
		c.sendMessage(G  + "  ██████╔╝██║   ██║██████╔╝   " + Y  + "██║██╔██╗ ██║██║  ██║█████╗   ╚███╔╝" + R);
		c.sendMessage(Y  + "  ██╔═══╝ ╚██╗ ██╔╝██╔═══╝    " + RE + "██║██║╚██╗██║██║  ██║██╔══╝   ██╔██╗" + R);
		c.sendMessage(Y  + "  ██║      ╚████╔╝ ██║        " + RE + "██║██║ ╚████║██████╔╝███████╗██╔╝ ██╗" + R);
		c.sendMessage(RE + "  ╚═╝       ╚═══╝  ╚═╝        " + RE + "╚═╝╚═╝  ╚═══╝╚═════╝ ╚══════╝╚═╝  ╚═╝" + R);
		c.sendMessage("");
		c.sendMessage("  " + G + "Version: " + W + version + GR + " | " + Y + "Platform: " + W + "Paper" + GR + " | " + RE + "Author: " + W + "PVP Index" + R);
		c.sendMessage("");
	}

	private void printStartupSummary(com.pvpindex.battles.version.VersionAdapter adapter, boolean papiEnabled, boolean vaultEnabled) {
		String GREEN = "\u001B[32m";
		String CYAN = "\u001B[36m";
		String YELLOW = "\u001B[33m";
		String GREY = "\u001B[90m";
		String WHITE = "\u001B[97m";
		String BOLD = "\u001B[1m";
		String RESET = "\u001B[0m";
		String TICK = GREEN + "✓" + RESET;
		String CROSS = GREY + "✗" + RESET;

		var console = getServer().getConsoleSender();
		console.sendMessage(CYAN + BOLD + "  ─── Startup Summary ───" + RESET);
		console.sendMessage("  " + WHITE + "Adapter     " + GREY + "│ " + GREEN + adapter.getClass().getSimpleName() + RESET);
		console.sendMessage("  " + WHITE + "MC Version  " + GREY + "│ " + GREEN + detectMinecraftVersion() + RESET);
		console.sendMessage("  " + WHITE + "Server      " + GREY + "│ " + GREEN + configManager.settings().serverId() + RESET);
		console.sendMessage("  " + WHITE + "Game Modes  " + GREY + "│ " + GREEN + gameModeRegistry.allModes().size() + RESET);
		console.sendMessage("  " + WHITE + "Kits        " + GREY + "│ " + GREEN + gameModeRegistry.allKits().size() + RESET);
		console.sendMessage("  " + WHITE + "Proxy       " + GREY + "│ " + (configManager.settings().proxyEnabled() ? TICK + " Enabled" : CROSS + GREY + " Disabled") + RESET);
		console.sendMessage("  " + WHITE + "PAPI        " + GREY + "│ " + (papiEnabled ? TICK + " Registered" : CROSS + GREY + " Not found") + RESET);
		console.sendMessage("  " + WHITE + "Vault       " + GREY + "│ " + (vaultEnabled ? TICK + " Economy rewards active" : CROSS + GREY + " Not found") + RESET);
		console.sendMessage("  " + WHITE + "Debug       " + GREY + "│ " + (configManager.settings().debug() ? YELLOW + "ON" : GREY + "OFF") + RESET);
		console.sendMessage("");
	}

	private void printSecurityWarnings() {
		String YELLOW = "\u001B[33m";
		String RED = "\u001B[31m";
		String BOLD = "\u001B[1m";
		String RESET = "\u001B[0m";

		boolean warned = false;

		String apiKey = configManager.settings().apiKey();
		if (apiKey == null || apiKey.isBlank() || "change-me".equalsIgnoreCase(apiKey)) {
			getLogger().warning(YELLOW + "API key is not configured. Set 'api.api_key' in config.yml to enable battle submissions." + RESET);
			warned = true;
		}

		if (configManager.settings().proxyEnabled()) {
			String secret = configManager.settings().proxySecret();
			if (secret == null || secret.isBlank()) {
				getLogger().warning(RED + BOLD + "Proxy messaging is enabled with NO shared secret! " + RESET
						+ YELLOW + "Any plugin on the channel can inject messages. Set 'proxy.secret' in config.yml." + RESET);
				warned = true;
			}
		}

		if (configManager.settings().debug()) {
			getLogger().warning(YELLOW + "Debug mode is ON. This produces verbose logging and should be disabled in production." + RESET);
			warned = true;
		}

		if (!warned) {
			getLogger().info("\u001B[32mAll safety checks passed.\u001B[0m");
		}
	}

	private void saveResourceIfAbsent(String resourcePath) {
		if (!new java.io.File(getDataFolder(), resourcePath).exists()
				&& getResource(resourcePath) != null) {
			saveResource(resourcePath, false);
		}
	}

	/**
	 * Schedule a repeating async task on Spigot/Paper (BukkitScheduler) and fall
	 * back to the Paper AsyncScheduler on Folia, which bans all BukkitScheduler calls.
	 */
	private void scheduleAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
		try {
			getServer().getScheduler().runTaskTimerAsynchronously(this, task, delayTicks, periodTicks);
		} catch (UnsupportedOperationException e) {
			getServer().getAsyncScheduler().runAtFixedRate(
					this, ignored -> task.run(),
					delayTicks * 50L, periodTicks * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Schedule a one-shot delayed async task, Folia-safe.
	 */
	private void scheduleAsyncDelayed(Runnable task, long delayTicks) {
		try {
			getServer().getScheduler().runTaskLaterAsynchronously(this, task, delayTicks);
		} catch (UnsupportedOperationException e) {
			getServer().getAsyncScheduler().runDelayed(
					this, ignored -> task.run(),
					delayTicks * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
		}
	}

	private String detectMinecraftVersion() {
		try {
			return (String) getServer().getClass().getMethod("getMinecraftVersion").invoke(getServer());
		} catch (Exception e) {
			// Spigot / non-Paper: parse from Bukkit version string e.g. "git-Spigot-xxx (MC: 1.21.4)"
			String ver = org.bukkit.Bukkit.getVersion();
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(MC: ([\\d.]+)\\)").matcher(ver);
			if (m.find()) return m.group(1);
			return org.bukkit.Bukkit.getBukkitVersion().split("-")[0];
		}
	}

	private com.pvpindex.battles.version.VersionAdapter resolveVersionAdapter() {
		String mcVersion = detectMinecraftVersion();
		getLogger().info("Minecraft version: " + mcVersion);

		// Paper 26.1.x uses the new "26.x.y" versioning scheme introduced alongside MC 1.21.4.
		// Do NOT rely on class detection here: RegistryAccess also ships with Folia/Paper 1.21.4,
		// which would cause Paper2610VersionAdapter to be loaded on the wrong server, crashing
		// with NoSuchFieldError (Attribute.MAX_HEALTH doesn't exist in the 1.21.4 API).
		if (mcVersion.startsWith("26.")) {
			try {
				return (com.pvpindex.battles.version.VersionAdapter)
						Class.forName("com.pvpindex.paper.v1_26_1.Paper2610VersionAdapter")
								.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				getLogger().severe("Failed to load Paper 26.1.x adapter: " + e.getMessage());
				return null;
			}
		}

		// Paper 1.21.x / Folia 1.21.x / Spigot 1.21.x
		try {
			Class.forName("org.bukkit.Registry");
			return (com.pvpindex.battles.version.VersionAdapter)
					Class.forName("com.pvpindex.paper.v1_21.Paper121VersionAdapter")
							.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException ignored) {}

		return null;
	}
}
