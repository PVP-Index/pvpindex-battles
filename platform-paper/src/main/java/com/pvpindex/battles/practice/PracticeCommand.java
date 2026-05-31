package com.pvpindex.battles.practice;

import com.pvpindex.battles.util.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the {@code /practice} command tree:
 * <ul>
 *   <li>{@code /practice}              — opens practice-mode selection GUI</li>
 *   <li>{@code /practice reaction [gameMode]} — start reaction training</li>
 *   <li>{@code /practice bot [gameMode]}      — start bot duel</li>
 *   <li>{@code /practice stop}         — end current practice session</li>
 * </ul>
 *
 * <p>Requires the {@code pvpindex.practice} permission (default: true).</p>
 */
public final class PracticeCommand implements CommandExecutor {

    private final PracticeManager practiceManager;
    private final MessageService messageService;

    public PracticeCommand(PracticeManager practiceManager, MessageService messageService) {
        this.practiceManager = practiceManager;
        this.messageService  = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("pvpindex.practice")) {
            messageService.send(player, "general.no_permission");
            return true;
        }

        if (args.length == 0) {
            practiceManager.openPracticeGui(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reaction" -> {
                String gameModeId = args.length >= 2 ? args[1] : null;
                practiceManager.startSession(player, PracticeMode.REACTION_TRAINING, gameModeId);
                yield true;
            }
            case "bot" -> {
                String gameModeId = args.length >= 2 ? args[1] : null;
                practiceManager.startSession(player, PracticeMode.BOT_DUEL, gameModeId);
                yield true;
            }
            case "stop" -> {
                practiceManager.endSession(player);
                yield true;
            }
            default -> {
                player.sendMessage("\u00A7cUsage: /practice [reaction|bot|stop] [gameMode]");
                yield true;
            }
        };
    }
}
