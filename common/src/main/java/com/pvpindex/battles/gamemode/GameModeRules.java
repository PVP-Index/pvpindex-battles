package com.pvpindex.battles.gamemode;

/**
 * Hard rules enforced by the {@code BattleService} / listeners while a battle
 * is in progress. Anything left null/zero falls back to vanilla behaviour.
 */
public record GameModeRules(
        int timeLimitSeconds,
        int countdownSeconds,
        boolean allowBlockBreak,
        boolean allowBlockPlace,
        boolean naturalRegeneration,
        boolean keepInventoryOnDeath,
        boolean allowItemDrops,
        boolean allowFoodLoss,
        boolean friendlyFire,
        int maxParticipants,
        int teamsCount,
        double startHealth,
        int startFoodLevel,
        boolean usePlayerInventory,
        int lootCooldownSeconds
) {
    public static GameModeRules vanilla() {
        // allowBlockBreak and allowBlockPlace default to false — arena safety.
        // Server owners can override per game mode in gamemodes.yml.
        return new GameModeRules(0, 5, false, false, true, false, true, true, false, 2, 2, 20.0d, 20, false, 0);
    }
}
