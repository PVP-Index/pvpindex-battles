package com.pvpindex.battles.config;

import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import java.util.Set;

public record PluginSettings(
        String apiBaseUrl,
        String apiKey,
        int timeoutSeconds,
        int retryAttempts,
        int retryInitialBackoffSeconds,
        double retryBackoffMultiplier,
        int retryMaxBackoffSeconds,
        int persistentRetryIntervalSeconds,
        boolean submitConfirmedOnly,
        String serverId,
        Set<GameModeType> enabledGameModes,
        Set<BattleType> enabledBattleTypes,
        ReplayDetailLevel replayDetailLevel,
        boolean writeLocalReplay,
        boolean autoSubmit,
        int autoSubmitDelaySeconds,
        long minimumBattleDurationSeconds,
        boolean markDisputedOnEarlyDisconnect,
        boolean debug,
        // Velocity tracking
        boolean velocityEnabled,
        double velocityThreshold,
        int velocityTrackingIntervalTicks,
        // Batched heartbeat
        boolean battleBatchEnabled,
        int battleBatchFlushIntervalTicks,
        int battleBatchMaxSize,
        // Stale-data cleanup
        int cleanupIntervalTicks,
        // Velocity proxy integration
        boolean proxyEnabled,
        String proxySecret,
        int proxyHeartbeatIntervalTicks,
        // TeamsAPI guard
        boolean teamsGuardEnabled
) {}
