package com.pvpindex.battles.practice.bot;

import com.pvpindex.battles.gamemode.KitApplier;
import com.pvpindex.battles.gamemode.KitDefinition;
import com.pvpindex.battles.practice.PracticeSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Lifecycle manager for a single bot-duel practice session.
 *
 * <p>Call {@link #spawn(Plugin, Player, Location, KitDefinition, KitApplier, PracticeSettings)}
 * to create and start the session, then {@link #end(boolean)} when the duel is
 * finished (either the bot or the player died).</p>
 */
public final class BotSession {

    private final BotOpponent opponent;
    private final BotBehaviorTask behaviorTask;
    private final Player target;
    private boolean ended = false;

    private BotSession(BotOpponent opponent, BotBehaviorTask behaviorTask, Player target) {
        this.opponent     = opponent;
        this.behaviorTask = behaviorTask;
        this.target       = target;
    }

    /**
     * Spawn the bot entity, equip it, and start its AI.
     *
     * @param plugin     owning plugin
     * @param target     the player who will fight the bot
     * @param location   where the bot should spawn (typically a few blocks
     *                   in front of the player)
     * @param kit        kit to equip on the bot (and optionally the player)
     * @param kitApplier applier for kit contents
     * @param settings   practice configuration
     * @return the newly started session
     */
    public static BotSession spawn(Plugin plugin,
            Player target,
            Location location,
            KitDefinition kit,
            KitApplier kitApplier,
            PracticeSettings settings) {

        String botName = "§6PracticeBot";
        World world = target.getWorld();

        LivingEntity entity = BotPlayerFactory.spawn(plugin, world, location, botName, settings.botUseNms());
        BotOpponent opponent = new BotOpponent(entity);
        opponent.applyKit(kit, kitApplier);

        BotBehaviorTask task = new BotBehaviorTask(plugin, opponent, target, settings);
        task.start();

        return new BotSession(opponent, task, target);
    }

    /**
     * End the session and remove the bot.
     *
     * @param playerWon whether the player killed the bot (drives the summary message)
     */
    public void end(boolean playerWon) {
        if (ended) return;
        ended = true;
        behaviorTask.stop();
        opponent.remove();

        if (target.isOnline()) {
            Component title = playerWon
                    ? Component.text("Bot Defeated!", NamedTextColor.GREEN, TextDecoration.BOLD)
                    : Component.text("Practice Ended", NamedTextColor.GRAY, TextDecoration.BOLD);
            Component subtitle = playerWon
                    ? Component.text("Well done!", NamedTextColor.WHITE)
                    : Component.text("Better luck next time.", NamedTextColor.GRAY);
            target.showTitle(Title.title(title, subtitle,
                    Title.Times.times(
                            Duration.ofMillis(200),
                            Duration.ofSeconds(3),
                            Duration.ofMillis(500))));
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public BotOpponent opponent()       { return opponent; }
    public boolean isEnded()            { return ended; }
}
