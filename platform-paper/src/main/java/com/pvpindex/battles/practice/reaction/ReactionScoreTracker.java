package com.pvpindex.battles.practice.reaction;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Tracks per-session reaction-training statistics: hits, misses, combos,
 * and reaction times (in milliseconds).  Pure data — no Bukkit dependency.
 */
public final class ReactionScoreTracker {

    private int hits = 0;
    private int misses = 0;
    private int combo = 0;
    private int bestCombo = 0;
    private long bestTimeMs = Long.MAX_VALUE;
    private final List<Long> reactionTimes = new ArrayList<>();

    /** Register a successful hit with the given reaction time in ms. */
    public void recordHit(long reactionTimeMs) {
        hits++;
        combo++;
        if (combo > bestCombo) bestCombo = combo;
        if (reactionTimeMs < bestTimeMs) bestTimeMs = reactionTimeMs;
        reactionTimes.add(reactionTimeMs);
    }

    /** Register an expired target (miss). Resets the combo. */
    public void recordMiss() {
        misses++;
        combo = 0;
    }

    /** Action-bar component shown while the drill is active. */
    public Component formatActionBar() {
        long best = bestTimeMs == Long.MAX_VALUE ? 0L : bestTimeMs;
        return Component.text("⚡ ", NamedTextColor.YELLOW)
                .append(Component.text("Hits: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(hits), NamedTextColor.GREEN))
                .append(Component.text("  Misses: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(misses), NamedTextColor.RED))
                .append(Component.text("  Combo: ", NamedTextColor.GRAY))
                .append(Component.text("x" + combo, combo >= 5 ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                .append(Component.text("  Best: ", NamedTextColor.GRAY))
                .append(Component.text(best + "ms", NamedTextColor.AQUA));
    }

    /** Full summary title component shown at the end of the session. */
    public Component formatSummary() {
        long avg = reactionTimes.isEmpty() ? 0L
                : (long) reactionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long best = bestTimeMs == Long.MAX_VALUE ? 0L : bestTimeMs;
        return Component.text("Practice Summary", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Hits: " + hits + "  Misses: " + misses, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Best: " + best + "ms  Avg: " + avg + "ms", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("Best Combo: ×" + bestCombo, NamedTextColor.GOLD));
    }

    public int hits()         { return hits; }
    public int misses()       { return misses; }
    public int combo()        { return combo; }
    public int bestCombo()    { return bestCombo; }
    public long bestTimeMs()  { return bestTimeMs; }
}
