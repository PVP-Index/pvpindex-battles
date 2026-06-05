package com.pvpindex.battles.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.replay.ReplayFile;
import com.pvpindex.battles.storage.FileStorageService;
import com.pvpindex.battles.util.MessageService;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * In-game moderation root command (registered as {@code /pvpmod}).
 *
 * <pre>
 *   /pvpmod watch &lt;battleUuid&gt;
 *   /pvpmod replay &lt;battleUuid&gt;
 *   /pvpmod replay stop
 *   /pvpmod report &lt;playerName&gt; &lt;reason...&gt;
 *   /pvpmod reports
 *   /pvpmod ban &lt;playerName&gt; &lt;duration|perm&gt; &lt;reason...&gt;
 *   /pvpmod ban federated &lt;playerName&gt; &lt;duration|perm&gt; &lt;reason...&gt;
 *   /pvpmod unban &lt;playerName&gt;
 * </pre>
 */
public final class ModerationCommand implements CommandExecutor {
    private final ModerationService moderationService;
    private final BattleService battleService;
    private final FileStorageService storage;
    private final ObjectMapper mapper;
    private final String localServerId;
    private final MessageService messageService;

    public ModerationCommand(ModerationService moderationService, BattleService battleService,
                             FileStorageService storage, ObjectMapper mapper, String localServerId,
                             MessageService messageService) {
        this.moderationService = moderationService;
        this.battleService = battleService;
        this.storage = storage;
        this.mapper = mapper;
        this.localServerId = localServerId;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvpindex.mod") && !sender.hasPermission("pvpindex.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        if (args.length == 0) {
            messageService.sendRaw(sender, "moderation.usage");
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "watch" -> watch(sender, args);
            case "replay" -> replay(sender, args);
            case "report" -> report(sender, args);
            case "reports" -> listReports(sender);
            case "ban" -> ban(sender, args);
            case "unban" -> unban(sender, args);
            default -> false;
        };
    }

    private boolean watch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "moderation.watch_only_player");
            return true;
        }
        if (args.length < 2) return false;
        UUID battle = parseUuid(sender, args[1]);
        if (battle == null) return true;
        moderationService.watchLive(player, battle).ifPresentOrElse(
                s -> messageService.send(player, "moderation.now_spectating", "%uuid%", s.getUuid().toString()),
                () -> messageService.send(player, "battle.not_found")
        );
        return true;
    }

    private boolean replay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "moderation.replay_only_player");
            return true;
        }
        if (args.length >= 2 && "stop".equalsIgnoreCase(args[1])) {
            moderationService.stopReplay(player);
            messageService.send(player, "moderation.replay_stopped");
            return true;
        }
        if (args.length < 2) return false;
        UUID battle = parseUuid(sender, args[1]);
        if (battle == null) return true;
        File file = storage.replaysDir().resolve(battle + ".json").toFile();
        if (!file.exists()) {
            messageService.send(player, "moderation.no_replay_file");
            return true;
        }
        try {
            ReplayFile replay = mapper.readValue(file, ReplayFile.class);
            moderationService.startReplay(player, replay);
            messageService.send(player, "moderation.replay_playing", "%count%", String.valueOf(replay.frames().size()));
        } catch (Exception e) {
            messageService.send(player, "moderation.replay_failed", "%error%", e.getMessage());
        }
        return true;
    }

    private boolean report(CommandSender sender, String[] args) {
        if (!(sender instanceof Player reporter)) {
            messageService.send(sender, "moderation.report_only_player");
            return true;
        }
        if (args.length < 3) return false;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messageService.send(reporter, "general.player_not_found");
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        UUID battleUuid = battleService.activeBattles().stream()
                .filter(s -> s.getParticipants().stream().anyMatch(p -> p.getUuid().equals(target.getUniqueId())))
                .findFirst().map(s -> s.getUuid()).orElse(null);
        try {
            ReportEntry entry = moderationService.fileReport(reporter, target, battleUuid, reason);
            messageService.send(reporter, "moderation.report_filed", "%id%", entry.id().toString());
        } catch (Exception e) {
            messageService.send(reporter, "moderation.report_failed", "%error%", e.getMessage());
        }
        return true;
    }

    private boolean listReports(CommandSender sender) {
        var open = moderationService.reportService().openReports();
        if (open.isEmpty()) {
            messageService.sendRaw(sender, "moderation.no_open_reports");
            return true;
        }
        messageService.sendRaw(sender, "moderation.open_reports_header");
        for (ReportEntry entry : open) {
            sender.sendMessage("§7- §f" + entry.reportedPlayerName()
                    + " §7by §f" + entry.reporterName()
                    + " §7- §f" + entry.reason()
                    + (entry.battleUuid() != null ? " §8[" + entry.battleUuid() + "]" : ""));
        }
        return true;
    }

    private boolean ban(CommandSender sender, String[] args) {
        if (args.length < 4) return false;
        boolean federated = "federated".equalsIgnoreCase(args[1]);
        int offset = federated ? 2 : 1;
        if (args.length < offset + 3) return false;

        String name = args[offset];
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        Instant expiresAt = parseDuration(args[offset + 1]);
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, offset + 2, args.length));

        BanEntry.Scope scope = federated ? BanEntry.Scope.FEDERATED : BanEntry.Scope.LOCAL;
        Player issuer = sender instanceof Player p ? p : null;
        try {
            BanEntry entry = moderationService.ban(issuer, offlinePlayer.getUniqueId(),
                    offlinePlayer.getName() == null ? name : offlinePlayer.getName(),
                    reason, expiresAt, scope, null, localServerId);
            messageService.send(sender, "moderation.banned", "%player%", name, "%scope%", scope.toString(), "%id%", entry.id().toString());
        } catch (Exception e) {
            messageService.send(sender, "moderation.ban_failed", "%error%", e.getMessage());
        }
        return true;
    }

    private boolean unban(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[1]);
        try {
            boolean removed = moderationService.unban(offlinePlayer.getUniqueId());
            messageService.send(sender, removed ? "moderation.unbanned" : "moderation.no_active_ban");
        } catch (Exception e) {
            messageService.send(sender, "moderation.unban_failed", "%error%", e.getMessage());
        }
        return true;
    }

    private UUID parseUuid(CommandSender sender, String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            messageService.send(sender, "general.invalid_uuid");
            return null;
        }
    }

    private static Instant parseDuration(String raw) {
        if (raw == null) return null;
        if ("perm".equalsIgnoreCase(raw) || "permanent".equalsIgnoreCase(raw) || "forever".equalsIgnoreCase(raw)) {
            return null;
        }
        // Accept things like 30m, 2h, 7d
        try {
            char unit = raw.charAt(raw.length() - 1);
            long amount = Long.parseLong(raw.substring(0, raw.length() - 1));
            return switch (unit) {
                case 's' -> Instant.now().plus(Duration.ofSeconds(amount));
                case 'm' -> Instant.now().plus(Duration.ofMinutes(amount));
                case 'h' -> Instant.now().plus(Duration.ofHours(amount));
                case 'd' -> Instant.now().plus(Duration.ofDays(amount));
                default -> Instant.now().plus(Duration.ofSeconds(Long.parseLong(raw)));
            };
        } catch (Exception ex) {
            return null;
        }
    }
}
