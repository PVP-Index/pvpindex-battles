package com.pvpindex.battles.config;

import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.moderation.ModerationSettings;
import com.pvpindex.battles.replay.ReplayDetailLevel;
import com.pvpindex.battles.replay.ReplaySettings;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private PluginSettings settings;
    private ReplaySettings replaySettings;
    private ModerationSettings moderationSettings;
    private LobbySettings lobbySettings;
    private DatabaseSettings databaseSettings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        Set<GameModeType> gameModes = parseEnums(cfg.getStringList("enabled_game_modes"), GameModeType.class);
        Set<BattleType> battleTypes = parseEnums(cfg.getStringList("enabled_battle_types"), BattleType.class);

		ReplayDetailLevel detailLevel;
		try {
			detailLevel = ReplayDetailLevel.valueOf(cfg.getString("recording.detail_level", "HIGH").toUpperCase());
		} catch (IllegalArgumentException e) {
			plugin.getLogger().warning("Invalid recording.detail_level in config, falling back to HIGH.");
			detailLevel = ReplayDetailLevel.HIGH;
		}

		settings = new PluginSettings(
				cfg.getString("api.base_url", "https://api.pvpindex.com"),
				cfg.getString("api.api_key", ""),
				Math.max(1, cfg.getInt("api.timeout", 10)),
				Math.max(0, cfg.getInt("api.retry_attempts", 3)),
				Math.max(1, cfg.getInt("api.retry_initial_backoff_seconds", 5)),
				Math.max(1.0, cfg.getDouble("api.retry_backoff_multiplier", 3.0)),
				Math.max(1, cfg.getInt("api.retry_max_backoff_seconds", 300)),
				Math.max(0, cfg.getInt("api.persistent_retry_interval_seconds", 300)),
				cfg.getBoolean("api.submit_confirmed_only", false),
				cfg.getString("server.id", "default-server"),
				gameModes,
				battleTypes,
				detailLevel,
				cfg.getBoolean("recording.write_local_file", true),
				cfg.getBoolean("auto_submit.enabled", true),
				Math.max(0, cfg.getInt("auto_submit.delay_seconds", 5)),
				Math.max(0, cfg.getLong("anti_abuse.minimum_battle_duration_seconds", 10)),
				cfg.getBoolean("anti_abuse.mark_disputed_on_early_disconnect", true),
				cfg.getBoolean("debug", false),
				// Velocity tracking
				cfg.getBoolean("velocity.enabled", true),
				Math.max(0.0, cfg.getDouble("velocity.threshold", 0.1)),
				Math.max(1, cfg.getInt("velocity.tracking_interval_ticks", 2)),
				// Batched heartbeat
				cfg.getBoolean("battle_batch.enabled", true),
				Math.max(1, cfg.getInt("battle_batch.flush_interval_ticks", 40)),
				Math.max(1, cfg.getInt("battle_batch.max_batch_size", 20)),
				// Stale-data cleanup
				Math.max(1, cfg.getInt("cleanup.interval_ticks", 100)),
				// Velocity proxy integration
				cfg.getBoolean("proxy.enabled", false),
				cfg.getString("proxy.secret", ""),
				Math.max(1, cfg.getInt("proxy.heartbeat_interval_ticks", 200)),
				// TeamsAPI guard
				cfg.getBoolean("teams_guard.block_same_team", false)
		);

        replaySettings = new ReplaySettings(
                ReplayDetailLevel.valueOf(cfg.getString("recording.detail_level", "HIGH").toUpperCase()),
                cfg.getInt("recording.tick_rate", 20),
                cfg.getInt("recording.max_frames", 144_000),
                cfg.getBoolean("recording.compress", true),
                cfg.getBoolean("recording.keep_event_log", true)
        );

        moderationSettings = new ModerationSettings(
                cfg.getBoolean("moderation.federated_bans.enabled", false),
                cfg.getBoolean("moderation.federated_bans.enforce_inbound", false),
                cfg.getInt("moderation.federated_bans.sync_interval_seconds", 300),
                cfg.getBoolean("moderation.spectator_on_report", true),
                cfg.getString("moderation.ban_screen_message",
                        "&cYou are banned from this server.\n&7Reason: %reason%")
        );

        lobbySettings = new LobbySettings(
                cfg.getBoolean("lobby.enabled", false),
                cfg.getString("lobby.node_id", "lobby-us"),
                cfg.getString("lobby.region", "us"),
                cfg.getString("lobby.velocity_server_name", ""),
                cfg.getString("lobby.redis.host", "localhost"),
                cfg.getInt("lobby.redis.port", 6379),
                cfg.getString("lobby.redis.password", ""),
                cfg.getInt("lobby.redis.database", 0),
                Math.max(1, cfg.getInt("lobby.redis.pool_size", 4))
        );

        databaseSettings = new DatabaseSettings(
                cfg.getBoolean("database.enabled", false),
                cfg.getString("database.type", "none"),
                cfg.getString("database.mysql.host", "localhost"),
                cfg.getInt("database.mysql.port", 3306),
                cfg.getString("database.mysql.database", "pvpindex"),
                cfg.getString("database.mysql.username", "pvpindex"),
                cfg.getString("database.mysql.password", ""),
                Math.max(1, cfg.getInt("database.mysql.pool_size", 10)),
                cfg.getBoolean("database.mysql.ssl", false),
                cfg.getString("database.sqlite.file", "pvpindex.db"),
                cfg.getString("database.mongodb.uri", "mongodb://localhost:27017"),
                cfg.getString("database.mongodb.database", "pvpindex")
        );
    }

    private <E extends Enum<E>> Set<E> parseEnums(List<String> values, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        for (String value : values) {
            try {
                result.add(Enum.valueOf(type, value.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown enum value in config: " + value + " for " + type.getSimpleName());
            }
        }
        return result;
    }

    public PluginSettings settings() { return settings; }
    public ReplaySettings replaySettings() { return replaySettings == null ? ReplaySettings.defaults() : replaySettings; }
    public ModerationSettings moderationSettings() { return moderationSettings == null ? ModerationSettings.defaults() : moderationSettings; }
    public LobbySettings lobbySettings() { return lobbySettings == null ? LobbySettings.defaults() : lobbySettings; }
    public DatabaseSettings databaseSettings() { return databaseSettings == null ? DatabaseSettings.defaults() : databaseSettings; }
}
