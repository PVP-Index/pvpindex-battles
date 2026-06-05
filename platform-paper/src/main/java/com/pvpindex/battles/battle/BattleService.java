package com.pvpindex.battles.battle;

import com.pvpindex.battles.api.BattlePayloadFactory;
import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.api.PvPIndexApiClient.PostResult;
import com.pvpindex.battles.battle.type.BattleStatus;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.battle.type.ParticipantResult;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.event.PvPIndexBattleCreateEvent;
import com.pvpindex.battles.event.PvPIndexBattleDisputeEvent;
import com.pvpindex.battles.event.PvPIndexBattleFinishEvent;
import com.pvpindex.battles.event.PvPIndexBattleStartEvent;
import com.pvpindex.battles.event.PvPIndexBattleSubmitEvent;
import com.pvpindex.battles.replay.BattleReplayRecorder;
import com.pvpindex.battles.messaging.PaperMessenger;
import com.pvpindex.battles.replay.PacketCaptureService;
import com.pvpindex.battles.replay.ReplayFrame;
import com.pvpindex.battles.storage.FileStorageService;
import com.pvpindex.battles.world.ArenaInstance;
import com.pvpindex.battles.world.ArenaPoolService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class BattleService {
    private final Plugin plugin;
    private final PluginSettings settings;
    private final BattleReplayRecorder replayRecorder;
    private final PvPIndexApiClient apiClient;
    private final BattlePayloadFactory payloadFactory;
    private final FileStorageService storageService;
    private final Map<UUID, BattleSession> sessions = new ConcurrentHashMap<>();
    /** battle uuid → arena instance reserved for it (so we can release on cleanup). */
    private final Map<UUID, ArenaInstance> arenas = new ConcurrentHashMap<>();
    /** battle uuid → time-limit timeout task. */
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    /** battle uuid → per-player equipment/health snapshots captured at battle start. */
    private final Map<UUID, Map<UUID, BattleStartSetup>> startSetups = new ConcurrentHashMap<>();

    // Optional collaborators wired in PvPIndexBattlesPlugin.onEnable after construction
    // so the existing constructor signature stays backward compatible.
    private PlayerStateService playerStateService;
    private ArenaPoolService arenaPoolService;
    private PacketCaptureService packetCaptureService;
    private PaperMessenger paperMessenger;
    private BattleStartSetupService startSetupService;

    public BattleService(
            Plugin plugin,
            PluginSettings settings,
            BattleReplayRecorder replayRecorder,
            PvPIndexApiClient apiClient,
            BattlePayloadFactory payloadFactory,
            FileStorageService storageService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.replayRecorder = replayRecorder;
        this.apiClient = apiClient;
        this.payloadFactory = payloadFactory;
        this.storageService = storageService;
    }

    // -------------------------------------------------------------------------
    // Optional collaborators (set after construction by the plugin bootstrap)
    // -------------------------------------------------------------------------

    public void setPlayerStateService(PlayerStateService playerStateService) {
        this.playerStateService = playerStateService;
    }

    public void setArenaPoolService(ArenaPoolService arenaPoolService) {
        this.arenaPoolService = arenaPoolService;
    }

    public void setPacketCaptureService(PacketCaptureService packetCaptureService) {
        this.packetCaptureService = packetCaptureService;
    }

    public void setPaperMessenger(PaperMessenger paperMessenger) {
        this.paperMessenger = paperMessenger;
    }

    public void setStartSetupService(BattleStartSetupService startSetupService) {
        this.startSetupService = startSetupService;
    }

    /** Associate a generated arena with a battle so cleanup can release it. */
    public void attachArena(UUID battleUuid, ArenaInstance instance) {
        if (instance == null) return;
        arenas.put(battleUuid, instance);
        // Store the world name in session metadata so event handlers (e.g.
        // EntityDeathEvent) can filter entity deaths to only arena worlds.
        BattleSession session = sessions.get(battleUuid);
        if (session != null) {
            session.getMetadata().put("arena_world", instance.worldName());
        }
    }

    /** Schedule a hard time limit for the battle (auto-finish as draw on expiry). */
    public void scheduleTimeLimit(UUID battleUuid, int seconds) {
        if (seconds <= 0 || Bukkit.getServer() == null) return;
        BukkitTask existing = timeoutTasks.remove(battleUuid);
        if (existing != null) existing.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BattleSession session = sessions.get(battleUuid);
            if (session == null || session.getStatus() != BattleStatus.ACTIVE) return;
            session.getMetadata().put("timeout", true);
            // No winner on timeout → draw
            endAndCleanup(battleUuid, List.of());
        }, seconds * 20L);
        timeoutTasks.put(battleUuid, task);
    }

    public BattleSession createBattle(BattleType battleType, GameModeType mode, List<BattleParticipant> participants, String arenaId, Map<String, Object> metadata) {
        if (!settings.enabledBattleTypes().contains(battleType)) {
            throw new IllegalArgumentException("Battle type disabled: " + battleType);
        }
        if (!settings.enabledGameModes().contains(mode)) {
            throw new IllegalArgumentException("Game mode disabled: " + mode);
        }
        for (BattleParticipant p : participants) {
            if (hasActiveBattle(p.getUuid())) {
                throw new IllegalStateException("Participant already in active battle: " + p.getMinecraftUsername());
            }
        }

        BattleSession session = new BattleSession(UUID.randomUUID(), battleType, mode, settings.serverId(), arenaId);
        session.setStatus(BattleStatus.WAITING);
        participants.forEach(session::addParticipant);
        if (metadata != null) {
            session.getMetadata().putAll(metadata);
        }
        sessions.put(session.getUuid(), session);
        if (Bukkit.getServer() != null) {
            PvPIndexBattleCreateEvent createEvent = new PvPIndexBattleCreateEvent(session);
            Bukkit.getPluginManager().callEvent(createEvent);
            if (createEvent.isCancelled()) {
                sessions.remove(session.getUuid());
                return null;
            }
        }
        return session;
    }

    /**
     * Activates a battle that has been created and is in the WAITING state.
     *
     * @return {@code true} if the battle was started. {@code false} if a
     *         {@link PvPIndexBattleStartEvent} listener cancelled it (the
     *         battle is automatically cancelled via {@link #cancelBattle}).
     */
    public boolean startBattle(UUID uuid) {
        BattleSession session = require(uuid);

        if (Bukkit.getServer() != null) {
            PvPIndexBattleStartEvent startEvent = new PvPIndexBattleStartEvent(session);
            Bukkit.getPluginManager().callEvent(startEvent);
            if (startEvent.isCancelled()) {
                cancelBattle(uuid);
                return false;
            }
            if (startEvent.getStartMessage() != null) {
                session.getMetadata().put("custom_start_message", startEvent.getStartMessage());
            }
        }

        session.setStatus(BattleStatus.COUNTDOWN);
        session.markStarted();

        // Capture each participant's equipment/health state immediately after
        // kit application and before the battle clock starts. Persisted to disk
        // for crash-safety; included in the API payload on submission.
        if (startSetupService != null && Bukkit.getServer() != null) {
            Map<UUID, BattleStartSetup> setups = new java.util.LinkedHashMap<>();
            for (BattleParticipant p : session.getParticipants()) {
                Player online = Bukkit.getPlayer(p.getUuid());
                if (online != null && online.isOnline()) {
                    setups.put(p.getUuid(), startSetupService.capture(online));
                }
            }
            if (!setups.isEmpty()) {
                startSetups.put(uuid, setups);
                startSetupService.persist(uuid, setups);
            }
        }

        replayRecorder.start(session);
        replayRecorder.record(session, "battle_start", null, null, Map.of("status", "active"));
        if (packetCaptureService != null) {
            packetCaptureService.beginRecording(session);
        }
        if (paperMessenger != null) {
            try { paperMessenger.sendBattleStart(session); } catch (RuntimeException ignored) {}
        }
        return true;
    }

    public void finishBattle(UUID uuid, Collection<UUID> winners) {
        BattleSession session = require(uuid);
        if (session.getParticipants().isEmpty()) {
            throw new IllegalStateException("Battle has no participants");
        }
        session.markFinished();

        // A surrender is a deliberate, valid end. always record it regardless
        // of how long the battle lasted.
        boolean hasSurrender = session.getParticipants().stream()
                .anyMatch(p -> p.getResult() == ParticipantResult.LEFT);

        if (!hasSurrender && session.durationSeconds() < settings.minimumBattleDurationSeconds()) {
            session.setStatus(BattleStatus.CANCELLED);
            session.getMetadata().put("cancel_reason", "minimum_duration");
            return;
        }

        session.getWinners().clear();
        session.getWinners().addAll(winners);
        session.getLosers().clear();
        session.getLosers().addAll(session.getParticipants().stream()
                .map(BattleParticipant::getUuid)
                .filter(id -> !winners.contains(id))
                .toList());

        for (BattleParticipant participant : session.getParticipants()) {
            if (winners.contains(participant.getUuid())) {
                participant.setResult(ParticipantResult.WIN);
                if (session.getBattleType() == BattleType.PRACTICE_BATTLE) {
                    participant.setElo(null, null);
                }
            } else {
                // Preserve LEFT (surrender/forfeit). do not overwrite with LOSS.
                // BattlePayloadFactory maps LEFT -> "surrender" so the API and
                // website reflect the correct reason for the loss.
                if (participant.getResult() != ParticipantResult.LEFT) {
                    participant.setResult(ParticipantResult.LOSS);
                }
                if (session.getBattleType() == BattleType.PRACTICE_BATTLE) {
                    participant.setElo(null, null);
                }
            }
        }
        replayRecorder.record(session, "battle_end", null, null, Map.of("winners", winners.stream().map(UUID::toString).toList()));
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(new PvPIndexBattleFinishEvent(session));
        }
    }

    public void markPlayerLeftEarly(UUID battleUuid, UUID playerUuid) {
        BattleSession session = require(battleUuid);
        session.getParticipants().stream().filter(p -> p.getUuid().equals(playerUuid)).findFirst().ifPresent(p -> p.setResult(ParticipantResult.LEFT));
        if (settings.markDisputedOnEarlyDisconnect()) {
            session.setStatus(BattleStatus.DISPUTED);
            session.getMetadata().put("suspicious", true);
            session.getMetadata().put("reason", "early_disconnect");
        }
    }

    /**
     * Cancels a battle that hasn't started yet (e.g. a player disconnected during
     * the pre-battle countdown). Removes the session, releases the arena, and
     * restores the state of any online participants. No API submission is made.
     */
    public void cancelBattle(UUID battleUuid) {
        BattleSession session = sessions.get(battleUuid);
        if (session == null) return;
        session.setStatus(BattleStatus.CANCELLED);

        BukkitTask t = timeoutTasks.remove(battleUuid);
        if (t != null) t.cancel();

        if (packetCaptureService != null) {
            packetCaptureService.finishRecording(session);
        }

        if (playerStateService != null) {
            for (BattleParticipant p : session.getParticipants()) {
                Player online = Bukkit.getServer() == null ? null : Bukkit.getPlayer(p.getUuid());
                if (online != null && online.isOnline()) {
                    try { playerStateService.restore(online); } catch (RuntimeException ignored) {}
                }
            }
        }

        startSetups.remove(battleUuid);
        if (startSetupService != null) {
            startSetupService.cleanup(battleUuid);
        }

        ArenaInstance arena = arenas.remove(battleUuid);
        if (arena != null && arenaPoolService != null) {
            try { arenaPoolService.release(arena); } catch (RuntimeException ignored) {}
        }

        sessions.remove(battleUuid);
    }

    /**
     * Finish the battle and run the full post-battle cleanup pipeline:
     * cancel timeout, restore both players' pre-battle state, release the
     * arena back to the pool, and (if enabled) auto-submit to the API.
     *
     * <p>Safe to call multiple times. subsequent calls are no-ops once the
     * session has left the active states.</p>
     */
    public void endAndCleanup(UUID battleUuid, Collection<UUID> winners) {
        BattleSession session = sessions.get(battleUuid);
        if (session == null) return;
        if (session.getStatus() != BattleStatus.ACTIVE
                && session.getStatus() != BattleStatus.COUNTDOWN
                && session.getStatus() != BattleStatus.WAITING
                && session.getStatus() != BattleStatus.DISPUTED) {
            return; // already finished/cancelled/submitted
        }

        // Cancel scheduled timeout
        BukkitTask t = timeoutTasks.remove(battleUuid);
        if (t != null) t.cancel();

        // Capture surrendering players BEFORE finishBattle overwrites their result to LOSS.
        Set<UUID> surrendered = session.getParticipants().stream()
                .filter(p -> p.getResult() == ParticipantResult.LEFT)
                .map(BattleParticipant::getUuid)
                .collect(Collectors.toSet());

        try {
            finishBattle(battleUuid, winners);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[BattleService] finishBattle failed for " + battleUuid + ": " + e.getMessage());
        }

        // Restore each participant who is still online; offline players will
        // be restored by StateRestoreListener on their next join.
        if (playerStateService != null) {
            for (BattleParticipant p : session.getParticipants()) {
                Player online = Bukkit.getServer() == null ? null : Bukkit.getPlayer(p.getUuid());
                if (online != null && online.isOnline()) {
                    try {
                        playerStateService.restore(online);
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("[BattleService] State restore failed for "
                                + p.getMinecraftUsername() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Notify participants of their result via Adventure titles + sounds.
        if (session.getStatus() == BattleStatus.FINISHED && Bukkit.getServer() != null) {
            boolean isDraw = session.getWinners().isEmpty();
            boolean hasSurrender = !surrendered.isEmpty();
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700));
            for (BattleParticipant p : session.getParticipants()) {
                Player online = Bukkit.getPlayer(p.getUuid());
                if (online == null || !online.isOnline()) continue;
                if (isDraw) {
                    online.showTitle(Title.title(
                            Component.text("Draw", NamedTextColor.GRAY),
                            Component.text("The battle ended in a draw.", NamedTextColor.WHITE),
                            times));
                    online.sendMessage(Component.text("The battle ended in a draw.", NamedTextColor.GRAY));
                    online.playSound(Sound.sound(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                            Sound.Source.MASTER, 1.0f, 0.8f));
                } else if (surrendered.contains(p.getUuid())) {
                    online.showTitle(Title.title(
                            Component.text("Surrendered", NamedTextColor.YELLOW),
                            Component.text("You forfeited the battle.", NamedTextColor.WHITE),
                            times));
                    online.sendMessage(Component.text("You surrendered the battle.", NamedTextColor.YELLOW));
                    online.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO,
                            Sound.Source.MASTER, 1.0f, 1.0f));
                } else if (session.getWinners().contains(p.getUuid())) {
                    Component subtitle = hasSurrender
                            ? Component.text("Opponent surrendered.", NamedTextColor.GRAY)
                            : Component.text("Congratulations!", NamedTextColor.GRAY);
                    online.showTitle(Title.title(
                            Component.text("Victory!", NamedTextColor.GOLD)
                                    .decoration(TextDecoration.BOLD, true),
                            subtitle,
                            times));
                    online.sendMessage(hasSurrender
                            ? Component.text("You won! (Opponent surrendered)", NamedTextColor.GOLD)
                            : Component.text("You won the battle!", NamedTextColor.GOLD));
                    online.playSound(Sound.sound(org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE,
                            Sound.Source.MASTER, 1.0f, 1.0f));
                } else {
                    online.showTitle(Title.title(
                            Component.text("Defeated", NamedTextColor.RED)
                                    .decoration(TextDecoration.BOLD, true),
                            Component.text("Better luck next time.", NamedTextColor.WHITE),
                            times));
                    online.sendMessage(Component.text("You lost the battle.", NamedTextColor.RED));
                    online.playSound(Sound.sound(org.bukkit.Sound.ENTITY_WITHER_DEATH,
                            Sound.Source.MASTER, 0.5f, 1.2f));
                }
            }
        }

        // Release the arena back to the pool / destroy it
        ArenaInstance arena = arenas.remove(battleUuid);
        if (arena != null && arenaPoolService != null) {
            try {
                arenaPoolService.release(arena);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[BattleService] Arena release failed: " + e.getMessage());
            }
        }

        // Notify Velocity the battle is over.
        if (paperMessenger != null) {
            try { paperMessenger.sendBattleEnd(session); } catch (RuntimeException ignored) {}
        }

        // Auto-submit to API if enabled and the battle wasn't cancelled
        if (settings.autoSubmit() && session.getStatus() == BattleStatus.FINISHED) {
            int delay = Math.max(0, settings.autoSubmitDelaySeconds());
            if (delay > 0 && Bukkit.getServer() != null) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> safeSubmit(battleUuid), delay * 20L);
            } else {
                safeSubmit(battleUuid);
            }
        }
    }

    private void safeSubmit(UUID battleUuid) {
        try {
            submitBattle(battleUuid);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[BattleService] Auto-submit failed for "
                    + battleUuid + ": " + e.getMessage());
        }
    }

    public void cancelBattle(UUID uuid, String reason) {
        BattleSession session = require(uuid);
        session.setStatus(BattleStatus.CANCELLED);
        session.getMetadata().put("cancel_reason", reason);
    }

    public Optional<BattleSession> find(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public List<BattleSession> activeBattles() {
        return sessions.values().stream()
                .filter(s -> s.getStatus() == BattleStatus.ACTIVE || s.getStatus() == BattleStatus.COUNTDOWN || s.getStatus() == BattleStatus.WAITING)
                .toList();
    }

    public boolean hasActiveBattle(UUID playerUuid) {
        return activeBattles().stream()
                .flatMap(s -> s.getParticipants().stream())
                .anyMatch(p -> p.getUuid().equals(playerUuid));
    }

    /** Find the (single) active battle a player is currently participating in, if any. */
    public Optional<BattleSession> findActiveBattleFor(UUID playerUuid) {
        return activeBattles().stream()
                .filter(s -> s.getParticipants().stream().anyMatch(p -> p.getUuid().equals(playerUuid)))
                .findFirst();
    }

    /**
     * Returns the battle-start setup captured for a specific participant, if
     * available. Returns an empty Optional when the battle has not started yet,
     * the participant was offline at start, or the setup was never captured.
     */
    public Optional<BattleStartSetup> getStartSetup(UUID battleUuid, UUID playerUuid) {
        Map<UUID, BattleStartSetup> setups = startSetups.get(battleUuid);
        if (setups == null) return Optional.empty();
        return Optional.ofNullable(setups.get(playerUuid));
    }

    public void submitBattle(UUID uuid) {
        BattleSession session = require(uuid);

        // Stop frame capture and collect accumulated frames.
        List<ReplayFrame> frames = List.of();
        if (packetCaptureService != null) {
            frames = packetCaptureService.finishRecording(session);
        }

        Map<String, Object> replayData = new LinkedHashMap<>(replayRecorder.buildReplay(session));
        if (!frames.isEmpty()) {
            replayData.put("frames", frames);
        }

        // Resolve per-player start setups: prefer in-memory, fall back to the
        // persisted YAML (crash recovery path), then clean up from both.
        Map<UUID, java.util.Map<String, Object>> startSetupMaps = null;
        if (startSetupService != null) {
            Map<UUID, BattleStartSetup> setups = startSetups.remove(uuid);
            if (setups == null || setups.isEmpty()) {
                setups = startSetupService.load(uuid);
            }
            if (!setups.isEmpty()) {
                startSetupMaps = new java.util.LinkedHashMap<>();
                for (Map.Entry<UUID, BattleStartSetup> e : setups.entrySet()) {
                    startSetupMaps.put(e.getKey(), startSetupService.toApiMap(e.getValue()));
                }
            }
            startSetupService.cleanup(uuid);
        }

        Map<String, Object> payload = payloadFactory.toPayload(session, replayData, null, startSetupMaps);

        try {
            storageService.saveBattlePayload(uuid, payload);
            if (settings.writeLocalReplay()) {
                replayRecorder.writeReplay(storageService.replaysDir(), session);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to persist local battle files for " + uuid + ": " + e.getMessage());
        }

        submitWithRetry(session, payload, 1);
    }

    private void submitWithRetry(BattleSession session, Map<String, Object> payload, int attempt) {
        final int maxAttempts = Math.max(1, settings.retryAttempts() + 1); // attempt 1 = first try
        final UUID uuid = session.getUuid();
        plugin.getLogger().info("Submitting battle " + uuid + " to PvPIndex (attempt " + attempt + "/" + maxAttempts + ")");
        apiClient.submitBattle(payload).thenAccept(result -> {
            if (result.ok()) {
                plugin.getLogger().info("Battle " + uuid + " submitted successfully (HTTP " + result.statusCode() + ").");
                session.setStatus(BattleStatus.SUBMITTED);
                try { storageService.markSubmitted(uuid); } catch (IOException e) {
                    plugin.getLogger().warning("Could not write .submitted marker for " + uuid + ": " + e.getMessage());
                }
                if (Bukkit.getServer() != null) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.getPluginManager().callEvent(new PvPIndexBattleSubmitEvent(session)));
                }
                return;
            }

            // Non-retryable error (4xx other than 408/429): persist immediately for admin review.
            if (!result.retryable()) {
                plugin.getLogger().severe("Battle " + uuid + " rejected by API. " + result.describe()
                        + ". Payload persisted to failed-submissions/ for admin review.");
                persistFailed(uuid, payload);
                return;
            }

            if (attempt >= maxAttempts) {
                plugin.getLogger().severe("Battle " + uuid + " failed after " + maxAttempts
                        + " attempts. last error: " + result.describe()
                        + ". Payload persisted; will retry from disk every "
                        + settings.persistentRetryIntervalSeconds() + "s (or run /pvpindex retryfailed).");
                persistFailed(uuid, payload);
                return;
            }

            long backoffSeconds = computeBackoff(attempt);
            plugin.getLogger().warning("Battle " + uuid + " attempt " + attempt + " failed ("
                    + result.describe() + "); retrying in " + backoffSeconds + "s.");
            scheduleAsyncDelayed(() -> submitWithRetry(session, payload, attempt + 1), backoffSeconds);
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Unexpected error submitting battle " + uuid + ": "
                    + (ex.getCause() != null ? ex.getCause() : ex));
            persistFailed(uuid, payload);
            return null;
        });
    }

    private long computeBackoff(int attempt) {
        double seconds = settings.retryInitialBackoffSeconds()
                * Math.pow(Math.max(1.0, settings.retryBackoffMultiplier()), attempt - 1);
        return Math.min(settings.retryMaxBackoffSeconds(), Math.max(1L, (long) seconds));
    }

    private void persistFailed(UUID uuid, Map<String, Object> payload) {
        try {
            storageService.saveFailedSubmission(uuid, payload);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to persist failed-submission payload for " + uuid + ": " + e.getMessage());
        }
    }

    private void scheduleAsyncDelayed(Runnable task, long delaySeconds) {
        if (Bukkit.getServer() == null) {
            // Tests / no-server environments. fall back to a daemon thread.
            Thread t = new Thread(() -> {
                try { Thread.sleep(delaySeconds * 1000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                task.run();
            }, "pvpindex-retry");
            t.setDaemon(true);
            t.start();
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(1L, delaySeconds * 20L));
    }

    public int retryFailedSubmissions() {
        int successCount = 0;
        try {
            List<Path> pending = storageService.listFailedSubmissions();
            if (pending.isEmpty()) return 0;
            plugin.getLogger().info("Retrying " + pending.size() + " persisted battle submission(s)…");
            for (Path path : pending) {
                Map<String, Object> payload = BattlePayloadFactory.normalizeLegacyPayload(
                        storageService.readJson(path));
                PostResult result = apiClient.submitBattle(payload).join();
                if (result.ok()) {
                    storageService.delete(path);
                    // Write submitted marker in battles/ if we can infer the UUID from filename.
                    try {
                        String name = path.getFileName().toString().replace(".json", "");
                        storageService.markSubmitted(java.util.UUID.fromString(name));
                    } catch (IllegalArgumentException ignored) { /* filename not a UUID */ }
                    successCount++;
                    plugin.getLogger().info("Persisted submission " + path.getFileName() + " accepted (HTTP " + result.statusCode() + ").");
                } else if (!result.retryable()) {
                    // 4xx. will never succeed; move to a 'rejected' subdir so admins can inspect.
                    Path rejectedDir = storageService.failedSubmissionsDir().resolve("rejected");
                    java.nio.file.Files.createDirectories(rejectedDir);
                    java.nio.file.Files.move(path, rejectedDir.resolve(path.getFileName()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().severe("Persisted submission " + path.getFileName()
                            + " rejected by API. " + result.describe() + ". Moved to failed-submissions/rejected/.");
                } else {
                    plugin.getLogger().warning("Persisted submission " + path.getFileName()
                            + " still failing (" + result.describe() + "); leaving on disk for next pass.");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed retry pass: " + e.getMessage());
        }
        return successCount;
    }

    /** Number of battle payloads currently waiting on disk for retry. */
    public int pendingFailedSubmissionCount() {
        try {
            return storageService.listFailedSubmissions().size();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Scans the local {@code battles/} archive for any payload that was saved
     * to disk (i.e. {@link #submitBattle} ran) but never confirmed as received
     * by the API (no sibling {@code .submitted} sentinel).  This covers the
     * case where the Minecraft server crashed or was force-killed between
     * writing the local file and getting a successful HTTP response.
     *
     * <p>Unsubmitted payloads are copied into {@code failed-submissions/} so
     * the existing {@link #retryFailedSubmissions()} machinery picks them up on
     * the next scheduled pass (or immediately if {@code submitNow} is true).</p>
     *
     * @return the number of unsubmitted battles found and enqueued
     */
    public int syncUnsubmittedBattles(boolean submitNow) {
        List<Path> unsubmitted;
        try {
            unsubmitted = storageService.listUnsubmittedBattles();
        } catch (IOException e) {
            plugin.getLogger().warning("syncUnsubmittedBattles: could not scan battles/: " + e.getMessage());
            return 0;
        }

        if (unsubmitted.isEmpty()) return 0;

        plugin.getLogger().info("Found " + unsubmitted.size()
                + " local battle(s) not yet confirmed as submitted. queuing for sync…");

        int queued = 0;
        for (Path path : unsubmitted) {
            // Skip if already in failed-submissions (e.g. from a previous crash cycle).
            String fileName = path.getFileName().toString();
            Path failedPath = storageService.failedSubmissionsDir().resolve(fileName);
            if (java.nio.file.Files.exists(failedPath)) continue;

            try {
                Map<String, Object> payload = storageService.readJson(path);
                storageService.saveFailedSubmission(
                        java.util.UUID.fromString(fileName.replace(".json", "")), payload);
                queued++;
            } catch (IOException | IllegalArgumentException e) {
                plugin.getLogger().warning("syncUnsubmittedBattles: could not queue " + fileName + ": " + e.getMessage());
            }
        }

        if (queued > 0) {
            plugin.getLogger().info("Queued " + queued + " battle(s) for sync retry.");
            if (submitNow) {
                int recovered = retryFailedSubmissions();
                if (recovered > 0) plugin.getLogger().info("Sync recovered " + recovered + " battle(s) immediately.");
            }
        }
        return queued;
    }

    public void disputeBattle(UUID uuid, String reason) {
        BattleSession session = require(uuid);
        session.setStatus(BattleStatus.DISPUTED);
        session.getMetadata().put("dispute_reason", reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        payload.put("server_id", session.getServerId());
        apiClient.disputeBattle(uuid, payload).thenAccept(result -> {
            if (result.ok()) {
                plugin.getLogger().info("Dispute filed for battle " + uuid + " (HTTP " + result.statusCode() + ").");
            } else {
                plugin.getLogger().warning("Dispute submission for battle " + uuid + " failed: " + result.describe());
            }
        });
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(new PvPIndexBattleDisputeEvent(session, reason));
        }
    }

    private BattleSession require(UUID uuid) {
        BattleSession session = sessions.get(uuid);
        if (session == null) {
            throw new IllegalArgumentException("Battle not found: " + uuid);
        }
        return session;
    }

    public List<String> activeBattleIds() {
        return activeBattles().stream().map(s -> s.getUuid().toString()).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns UUIDs of any active battles whose arena world matches {@code worldName}.
     * Used by {@link com.pvpindex.battles.listener.WorldCleanupListener} to cancel
     * battles when their arena world is unexpectedly unloaded.
     */
    public List<UUID> findBattleUuidsForWorld(String worldName) {
        return arenas.entrySet().stream()
                .filter(e -> worldName.equals(e.getValue().worldName()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
