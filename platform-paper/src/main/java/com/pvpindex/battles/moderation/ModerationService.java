package com.pvpindex.battles.moderation;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.replay.PacketReplayBridge;
import com.pvpindex.battles.replay.ReplayFile;
import com.pvpindex.battles.replay.ReplayPlayback;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Top-level facade used by the in-game moderator commands. Coordinates the
 * spectator system, report store, ban store, and replay playback. Designed so
 * a moderator can do everything without leaving Minecraft:
 *
 * <pre>
 *   /pvpmod watch &lt;battle&gt;          // join a live battle as a ghost
 *   /pvpmod replay &lt;battle&gt;         // re-watch a finished battle
 *   /pvpmod report &lt;player&gt; &lt;why&gt;
 *   /pvpmod ban &lt;player&gt; [duration] &lt;reason&gt;
 *   /pvpmod ban -federated &lt;player&gt; ...   // network-wide
 *   /pvpmod unban &lt;player&gt;
 *   /pvpmod reports                  // list open reports
 * </pre>
 */
public final class ModerationService {
    private final Plugin plugin;
    private final BattleService battleService;
    private final BanService banService;
    private final ReportService reportService;
    private final ModerationSettings settings;
    private final PacketReplayBridge replayBridge;
    private final Map<UUID, ReplayPlayback> activePlaybacks = new ConcurrentHashMap<>();

    public ModerationService(
            Plugin plugin,
            BattleService battleService,
            BanService banService,
            ReportService reportService,
            ModerationSettings settings,
            PacketReplayBridge replayBridge
    ) {
        this.plugin = plugin;
        this.battleService = battleService;
        this.banService = banService;
        this.reportService = reportService;
        this.settings = settings;
        this.replayBridge = replayBridge;
    }

    // ---- Live spectating ---------------------------------------------------

    public Optional<BattleSession> watchLive(Player moderator, UUID battleUuid) {
        Optional<BattleSession> session = battleService.find(battleUuid);
        session.ifPresent(s -> {
            moderator.setGameMode(org.bukkit.GameMode.SPECTATOR);
            // Teleport to first participant for instant context.
            s.getParticipants().stream().findFirst().ifPresent(p -> {
                Player target = plugin.getServer().getPlayer(p.getUuid());
                if (target != null) {
                    moderator.teleport(target);
                    moderator.setSpectatorTarget(target);
                }
            });
        });
        return session;
    }

    // ---- Replay playback ---------------------------------------------------

    public void startReplay(Player moderator, ReplayFile replay) {
        stopReplay(moderator);
        ReplayPlayback playback = new ReplayPlayback(plugin, replayBridge, replay, moderator);
        activePlaybacks.put(moderator.getUniqueId(), playback);
        playback.start();
    }

    public void stopReplay(Player moderator) {
        ReplayPlayback existing = activePlaybacks.remove(moderator.getUniqueId());
        if (existing != null) existing.stop();
    }

    public Optional<ReplayPlayback> activePlayback(UUID moderator) {
        return Optional.ofNullable(activePlaybacks.get(moderator));
    }

    // ---- Reports -----------------------------------------------------------

    public ReportEntry fileReport(Player reporter, Player target, UUID battleUuid, String reason) throws java.io.IOException {
        ReportEntry entry = new ReportEntry(
                UUID.randomUUID(),
                target.getUniqueId(), target.getName(),
                reporter.getUniqueId(), reporter.getName(),
                battleUuid,
                reason,
                Instant.now(),
                ReportEntry.ReportStatus.OPEN
        );
        return reportService.submit(entry);
    }

    // ---- Bans --------------------------------------------------------------

    public BanEntry ban(Player issuer, UUID targetUuid, String targetName, String reason,
                        Instant expiresAt, BanEntry.Scope scope, UUID battleUuid, String serverId) throws java.io.IOException {
        BanEntry entry = new BanEntry(
                UUID.randomUUID(), targetUuid, targetName,
                issuer == null ? null : issuer.getUniqueId(),
                issuer == null ? "console" : issuer.getName(),
                reason, Instant.now(), expiresAt, scope, battleUuid, serverId
        );
        BanEntry saved = banService.ban(entry);
        Player online = plugin.getServer().getPlayer(targetUuid);
        if (online != null && online.isOnline()) {
            online.kick(net.kyori.adventure.text.Component.text(
                    settings.banScreenMessage().replace("%reason%", reason).replace('&', '§')));
        }
        return saved;
    }

    public boolean unban(UUID playerUuid) throws java.io.IOException {
        return banService.unban(playerUuid);
    }

    public Optional<BanEntry> activeBan(UUID playerUuid) {
        return banService.activeBan(playerUuid, settings.enforceInboundFederatedBans());
    }

    public ModerationSettings settings() { return settings; }
    public BanService banService() { return banService; }
    public ReportService reportService() { return reportService; }
}
