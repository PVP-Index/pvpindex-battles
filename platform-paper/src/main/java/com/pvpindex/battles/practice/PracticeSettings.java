package com.pvpindex.battles.practice;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * All configurable values for the practice system, loaded from {@code practice.yml}.
 */
public final class PracticeSettings {

    private final boolean enabled;

    // ── Reaction training ────────────────────────────────────────────────
    private final int reactionMaxTargets;
    private final int reactionSpawnIntervalTicks;
    private final int reactionTargetLifetimeTicks;

    // ── Bot duel ─────────────────────────────────────────────────────────
    private final String botKitId;
    private final int botAttackIntervalTicks;
    private final double botMoveSpeed;
    private final double botAttackDamage;
    private final boolean botUseNms;

    public PracticeSettings(boolean enabled,
            int reactionMaxTargets,
            int reactionSpawnIntervalTicks,
            int reactionTargetLifetimeTicks,
            String botKitId,
            int botAttackIntervalTicks,
            double botMoveSpeed,
            double botAttackDamage,
            boolean botUseNms) {
        this.enabled = enabled;
        this.reactionMaxTargets = reactionMaxTargets;
        this.reactionSpawnIntervalTicks = reactionSpawnIntervalTicks;
        this.reactionTargetLifetimeTicks = reactionTargetLifetimeTicks;
        this.botKitId = botKitId;
        this.botAttackIntervalTicks = botAttackIntervalTicks;
        this.botMoveSpeed = botMoveSpeed;
        this.botAttackDamage = botAttackDamage;
        this.botUseNms = botUseNms;
    }

    /** Load from a {@code practice.yml} YamlConfiguration. */
    public static PracticeSettings from(YamlConfiguration yaml) {
        return new PracticeSettings(
                yaml.getBoolean("practice.enabled", true),
                yaml.getInt("practice.reaction_training.max_targets", 5),
                yaml.getInt("practice.reaction_training.spawn_interval_ticks", 40),
                yaml.getInt("practice.reaction_training.target_lifetime_ticks", 100),
                yaml.getString("practice.bot.kit_id", "sword_starter"),
                yaml.getInt("practice.bot.attack_interval_ticks", 10),
                yaml.getDouble("practice.bot.move_speed", 0.28),
                yaml.getDouble("practice.bot.attack_damage", 3.0),
                yaml.getBoolean("practice.bot.use_nms_fake_player", true)
        );
    }

    /** @return sensible hard-coded defaults for unit tests / fresh installs. */
    public static PracticeSettings defaults() {
        return new PracticeSettings(true, 5, 40, 100, "sword_starter", 10, 0.28, 3.0, true);
    }

    public boolean enabled()                   { return enabled; }
    public int reactionMaxTargets()            { return reactionMaxTargets; }
    public int reactionSpawnIntervalTicks()    { return reactionSpawnIntervalTicks; }
    public int reactionTargetLifetimeTicks()   { return reactionTargetLifetimeTicks; }
    public String botKitId()                   { return botKitId; }
    public int botAttackIntervalTicks()        { return botAttackIntervalTicks; }
    public double botMoveSpeed()               { return botMoveSpeed; }
    public double botAttackDamage()            { return botAttackDamage; }
    public boolean botUseNms()                 { return botUseNms; }
}
