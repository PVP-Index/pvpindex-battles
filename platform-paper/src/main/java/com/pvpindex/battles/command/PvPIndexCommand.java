package com.pvpindex.battles.command;

import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.config.ConfigManager;
import com.pvpindex.battles.storage.FileStorageService;
import com.pvpindex.battles.util.MessageService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PvPIndexCommand implements CommandExecutor {
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final BattleService battleService;
    private final FileStorageService fileStorageService;
    private final PvPIndexApiClient apiClient;

    public PvPIndexCommand(ConfigManager configManager, MessageService messageService, BattleService battleService, FileStorageService fileStorageService, PvPIndexApiClient apiClient) {
        this.configManager = configManager;
        this.messageService = messageService;
        this.battleService = battleService;
        this.fileStorageService = fileStorageService;
        this.apiClient = apiClient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messageService.send(sender, "status.ok", "%count%", String.valueOf(battleService.activeBattles().size()));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "battle" -> handleBattle(sender, Arrays.copyOfRange(args, 1, args.length));
            case "battles" -> handleBattles(sender);
            case "replay" -> handleReplay(sender, Arrays.copyOfRange(args, 1, args.length));
            case "retryfailed" -> handleRetry(sender);
            case "submissions" -> handleSubmissions(sender);
            case "sync" -> handleSync(sender);
            case "verify" -> handleVerify(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> false;
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("pvpindex.reload") && !sender.hasPermission("pvpindex.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        configManager.reload();
        messageService.reload();
        messageService.send(sender, "status.reloaded");
        return true;
    }

    private boolean handleBattle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "create" -> createBattle(sender, args);
            case "start" -> startBattle(sender, args);
            case "cancel" -> cancelBattle(sender, args);
            case "finish" -> finishBattle(sender, args);
            case "submit" -> submitBattle(sender, args);
            case "dispute" -> disputeBattle(sender, args);
            default -> false;
        };
    }

	private boolean createBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.create")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 4) {
			return false;
		}
		BattleType type;
		GameModeType mode;
		try {
			type = BattleType.valueOf(args[1].toUpperCase());
			mode = GameModeType.valueOf(args[2].toUpperCase());
		} catch (IllegalArgumentException e) {
			messageService.sendRaw(sender, "general.invalid_argument");
			return true;
		}
        List<BattleParticipant> participants = Arrays.stream(Arrays.copyOfRange(args, 3, args.length))
                .map(Bukkit::getOfflinePlayer)
                .map(p -> new BattleParticipant(p.getUniqueId(), p.getName() == null ? "unknown" : p.getName(), null))
                .toList();
		BattleSession session = battleService.createBattle(type, mode, participants, null, Map.of());
		if (session == null) {
			messageService.send(sender, "general.cancelled");
			return true;
		}
		messageService.send(sender, "battle.created", "%uuid%", session.getUuid().toString());
		return true;
    }

	private boolean startBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.start")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 2) return false;
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		battleService.startBattle(uuid);
		messageService.send(sender, "battle.started", "%uuid%", uuid.toString());
		return true;
	}

	private boolean cancelBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.cancel")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 2) return false;
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		battleService.cancelBattle(uuid);
		messageService.send(sender, "battle.cancelled", "%uuid%", uuid.toString());
		return true;
	}

	private boolean finishBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.finish")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 2) return false;
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		var session = battleService.find(uuid);
		if (session.isEmpty()) {
			messageService.sendRaw(sender, "general.not_found");
			return true;
		}
		UUID winner = session.get().getParticipants().getFirst().getUuid();
		battleService.finishBattle(uuid, List.of(winner));
		messageService.send(sender, "battle.finished", "%uuid%", uuid.toString());
		return true;
	}

	private boolean submitBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.submit")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 2) return false;
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		battleService.submitBattle(uuid);
		messageService.send(sender, "battle.submitted", "%uuid%", uuid.toString());
		return true;
	}

	private boolean disputeBattle(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.battle.dispute")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 3) return false;
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
		battleService.disputeBattle(uuid, reason);
		messageService.send(sender, "battle.disputed", "%uuid%", uuid.toString());
		return true;
	}

    private boolean handleBattles(CommandSender sender) {
        messageService.send(sender, "admin.active_battles", "%ids%", String.join(", ", battleService.activeBattleIds()));
        return true;
    }

	private boolean handleReplay(CommandSender sender, String[] args) {
		if (!hasPermissionOrAdmin(sender, "pvpindex.replay.export")) {
			messageService.send(sender, "general.no_permission");
			return true;
		}
		if (args.length < 2 || !"export".equalsIgnoreCase(args[0])) {
			return false;
		}
		UUID uuid = parseUuid(sender, args[1]);
		if (uuid == null) return true;
		try {
			var session = battleService.find(uuid);
			if (session.isEmpty()) {
				messageService.sendRaw(sender, "general.not_found");
				return true;
			}
			var file = fileStorageService.replaysDir().resolve(uuid + ".json");
			messageService.send(sender, "replay.exported", "%file%", file.toString());
			return true;
		} catch (Exception e) {
			messageService.send(sender, "admin.replay_export_failed", "%error%", e.getMessage());
			return true;
		}
	}

    private boolean handleRetry(CommandSender sender) {
        if (!hasPermissionOrAdmin(sender, "pvpindex.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        messageService.sendRaw(sender, "admin.retry_started");
        int count = battleService.retryFailedSubmissions();
        messageService.send(sender, "admin.retry_result", "%count%", String.valueOf(count));
        return true;
    }

    private boolean handleSubmissions(CommandSender sender) {
        if (!hasPermissionOrAdmin(sender, "pvpindex.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }

        // Failed-submissions queue (HTTP failures waiting to retry)
        int pending = battleService.pendingFailedSubmissionCount();
        messageService.sendRaw(sender, "admin.submissions_header", "%count%", String.valueOf(pending));
        if (pending > 0) {
            messageService.sendRaw(sender, "admin.submissions_hint");
            messageService.sendRaw(sender, "admin.submissions_retry_hint");
            try {
                java.util.List<java.nio.file.Path> files = fileStorageService.listFailedSubmissions();
                int show = Math.min(10, files.size());
                for (int i = 0; i < show; i++) {
                    sender.sendMessage("§8  • §7" + files.get(i).getFileName());
                }
                if (files.size() > show) sender.sendMessage("§8  … and " + (files.size() - show) + " more.");
            } catch (java.io.IOException e) {
                messageService.send(sender, "admin.submissions_list_failed", "%error%", e.getMessage());
            }
        }

        // Local battles not yet confirmed as submitted (crash-recovery candidates)
        try {
            java.util.List<java.nio.file.Path> unsubmitted = fileStorageService.listUnsubmittedBattles();
            if (!unsubmitted.isEmpty()) {
                messageService.sendRaw(sender, "admin.unsubmitted_header", "%count%", String.valueOf(unsubmitted.size()));
                messageService.sendRaw(sender, "admin.unsubmitted_hint");
                int show = Math.min(10, unsubmitted.size());
                for (int i = 0; i < show; i++) {
                    sender.sendMessage("§8  • §7" + unsubmitted.get(i).getFileName());
                }
                if (unsubmitted.size() > show) sender.sendMessage("§8  … and " + (unsubmitted.size() - show) + " more.");
            } else {
                messageService.sendRaw(sender, "admin.all_submitted");
            }
        } catch (java.io.IOException e) {
            messageService.send(sender, "admin.unsubmitted_check_failed", "%error%", e.getMessage());
        }

        return true;
    }

    private boolean handleVerify(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.sendRaw(sender, "admin.verify_players_only");
            return true;
        }
        if (args.length < 1) {
            messageService.sendRaw(player, "admin.verify_usage");
            return true;
        }
        String code = args[0].toUpperCase();
        messageService.sendRaw(player, "admin.verify_in_progress", "%code%", code);

		apiClient.verifyMinecraft(player.getUniqueId(), player.getName(), code)
				.thenAccept(result ->
					Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), () -> {
						if (!player.isOnline()) return;
						if (result.ok()) {
							messageService.sendRaw(player, "admin.verify_success");
							messageService.sendRaw(player, "admin.verify_claim_url");
						} else if (result.statusCode() == 404) {
							messageService.sendRaw(player, "admin.verify_invalid_code");
						} else if (result.statusCode() == 409) {
							messageService.sendRaw(player, "admin.verify_already_linked");
						} else {
							messageService.sendRaw(player, "admin.verify_failed", "%detail%", String.valueOf(result.statusCode()));
						}
					})
				)
				.exceptionally(ex -> {
					Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("PvPIndexBattles"), () -> {
						if (player.isOnline()) {
							messageService.sendRaw(player, "admin.verify_failed", "%detail%", ex.getMessage());
						}
					});
					return null;
				});
        return true;
    }

	private UUID parseUuid(CommandSender sender, String input) {
		try {
			return UUID.fromString(input);
		} catch (IllegalArgumentException e) {
			messageService.sendRaw(sender, "general.invalid_argument");
			return null;
		}
	}

	private boolean hasPermissionOrAdmin(CommandSender sender, String permission) {
		return sender.hasPermission(permission) || sender.hasPermission("pvpindex.admin");
	}

    private boolean handleSync(CommandSender sender) {
        if (!hasPermissionOrAdmin(sender, "pvpindex.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        messageService.sendRaw(sender, "admin.sync_scanning");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            int queued = battleService.syncUnsubmittedBattles(true);
            if (queued == 0) {
                messageService.sendRaw(sender, "admin.sync_nothing");
            } else {
                messageService.sendRaw(sender, "admin.sync_queued", "%count%", String.valueOf(queued));
            }
        });
        return true;
    }
}
