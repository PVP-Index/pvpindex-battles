package com.pvpindex.battles.moderation;

/**
 * Tunable knobs for the moderation subsystem. Loaded by {@code ConfigManager}
 * alongside the legacy {@code PluginSettings}.
 *
 * @param federatedBansEnabled       allow this server to publish bans to the
 *                                   PvPIndex network
 * @param enforceInboundFederatedBans honour bans published by other verified
 *                                    servers (off by default — this is the
 *                                    user-requested opt-in)
 * @param federatedBanSyncIntervalSeconds how often to pull the network ban list
 * @param spectatorOnReportPriority  auto-jump moderators into the offender's
 *                                    live battle when a new report opens
 */
public record ModerationSettings(
        boolean federatedBansEnabled,
        boolean enforceInboundFederatedBans,
        int federatedBanSyncIntervalSeconds,
        boolean spectatorOnReportPriority,
        String banScreenMessage
) {
    public static ModerationSettings defaults() {
        return new ModerationSettings(false, false, 300, true,
                "&cYou are banned from this server.\n&7Reason: %reason%");
    }
}
