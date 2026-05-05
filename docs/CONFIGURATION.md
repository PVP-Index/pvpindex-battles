# Configuration Reference

## Paper Plugin

Generated at `plugins/PvPIndexBattles/config.yml` on first run.

### Language

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `language` | String | `"en"` | Language for player-facing messages. Loads `lang/<code>.yml` from the plugin folder. Bundled languages: `en`, `nl`, `de`, `pl`, `zh`, `es`. Drop custom files into `plugins/PvPIndexBattles/lang/`. |

### API

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `api.base_url` | String | `https://api.pvpindex.com` | PvPIndex API base URL |
| `api.api_key` | String | `change-me` | Bearer token for API auth |
| `api.timeout` | int | `10` | HTTP timeout in seconds |
| `api.retry_attempts` | int | `3` | Retries on failed POST |
| `api.retry_initial_backoff_seconds` | int | `5` | First retry delay |
| `api.retry_backoff_multiplier` | double | `3.0` | Exponential backoff multiplier |
| `api.retry_max_backoff_seconds` | int | `300` | Max backoff cap |
| `api.persistent_retry_interval_seconds` | int | `300` | Background retry interval (0 = disable) |
| `api.submit_confirmed_only` | boolean | `false` | Only submit battles confirmed by both participants |

### Server

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `server.id` | String | `default-server` | Unique ID for this server instance |

### Game Modes and Battle Types

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled_game_modes` | List | all bundled modes | Which game modes are active |
| `enabled_battle_types` | List | all 7 types | Which battle types are allowed |

### Recording (Replay)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `recording.detail_level` | String | `HIGH` | Replay fidelity: `LOW`, `MEDIUM`, `HIGH` |
| `recording.tick_rate` | int | `20` | Samples per second |
| `recording.max_frames` | int | `144000` | Max frames per battle |
| `recording.compress` | boolean | `true` | GZIP compress replay files |
| `recording.keep_event_log` | boolean | `true` | Keep timestamped event log |
| `recording.write_local_file` | boolean | `true` | Save replay to disk |

### Auto Submit

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `auto_submit.enabled` | boolean | `true` | Auto-submit battles to API |
| `auto_submit.delay_seconds` | int | `5` | Delay before auto-submit |

### Anti-Abuse

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `anti_abuse.minimum_battle_duration_seconds` | int | `10` | Min battle length to count |
| `anti_abuse.mark_disputed_on_early_disconnect` | boolean | `true` | Flag battle on early quit |

### Arena Pool

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `arena_pool.enabled` | boolean | `true` | Pre-generate arena worlds |
| `arena_pool.warm_size_per_template` | int | `2` | Arenas kept warm per template |
| `arena_pool.refill_async` | boolean | `true` | Generate arenas asynchronously |

### Player State

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `player_state.include_ender_chest` | boolean | `true` | Save/restore ender chest in snapshots |

### Moderation

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `moderation.spectator_on_report` | boolean | `true` | Put reporter in spectator when watching |
| `moderation.ban_screen_message` | String | *(see config)* | Ban kick message (`%reason%` interpolated) |
| `moderation.federated_bans.enabled` | boolean | `false` | Publish bans to the PvPIndex network |
| `moderation.federated_bans.enforce_inbound` | boolean | `false` | Block players banned on other servers |
| `moderation.federated_bans.sync_interval_seconds` | int | `300` | Sync interval for federated bans |

### Velocity (Motion Tracking)

Not the proxy. This tracks player movement velocity for replay recording.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `velocity.enabled` | boolean | `true` | Enable per-tick velocity tracking |
| `velocity.threshold` | double | `0.1` | Min speed delta (blocks/tick) to record |
| `velocity.tracking_interval_ticks` | int | `2` | Tick sampling interval |

### Battle Batch Scheduler

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `battle_batch.enabled` | boolean | `true` | Send periodic heartbeat to API |
| `battle_batch.flush_interval_ticks` | int | `40` | Ticks between flushes |
| `battle_batch.max_batch_size` | int | `20` | Max battles per flush |

### Cleanup

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `cleanup.interval_ticks` | int | `100` | Ticks between stale-data cleanup |

### Proxy (Velocity Integration)

See [SETUP-VELOCITY.md](SETUP-VELOCITY.md) for a full walkthrough.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `proxy.enabled` | boolean | `false` | Enable Velocity proxy messaging |
| `proxy.secret` | String | `""` | Shared secret (must match Velocity plugin) |
| `proxy.heartbeat_interval_ticks` | int | `200` | Heartbeat to proxy interval |

### Lobby Mode (Redis Global Features)

See [SETUP-LOBBY.md](SETUP-LOBBY.md) for a full walkthrough and [GLOBAL-FEATURES.md](GLOBAL-FEATURES.md) for feature details.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `lobby.enabled` | boolean | `false` | Enable lobby mode (direct Redis connectivity for global features) |
| `lobby.node_id` | String | `lobby-1` | Unique identifier for this lobby server |
| `lobby.region` | String | `default` | Region label used for routing decisions |
| `lobby.redis.host` | String | `localhost` | Redis server hostname |
| `lobby.redis.port` | int | `6379` | Redis server port |
| `lobby.redis.password` | String | *(empty)* | Redis AUTH password |
| `lobby.redis.database` | int | `0` | Redis database index (0-15) |
| `lobby.redis.pool_size` | int | `8` | Jedis connection pool size |

### Database (Optional Persistence)

See [SETUP-DATABASE.md](SETUP-DATABASE.md) for a full walkthrough.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `database.enabled` | boolean | `false` | Enable persistent storage |
| `database.type` | String | `none` | Backend type: `mysql`, `sqlite`, `mongodb`, or `none` |
| `database.mysql.host` | String | `localhost` | MySQL server hostname |
| `database.mysql.port` | int | `3306` | MySQL server port |
| `database.mysql.database` | String | `pvpindex` | MySQL database name |
| `database.mysql.username` | String | `root` | MySQL username |
| `database.mysql.password` | String | *(empty)* | MySQL password |
| `database.mysql.pool_size` | int | `10` | HikariCP max connections |
| `database.mysql.ssl` | boolean | `false` | Use SSL for MySQL connections |
| `database.sqlite.file` | String | `pvpindex.db` | SQLite database file path (relative to plugin folder) |
| `database.mongodb.uri` | String | *(empty)* | MongoDB connection URI |
| `database.mongodb.database` | String | `pvpindex` | MongoDB database name |

When `lobby.enabled` is `true`, the server starts `LobbyNetworkService` and its dependent services: `PlayerSyncService`, `ChallengeSyncService`, `PresenceService`, `InviteService`, `PartySyncService`, `RoutingService`, `TransferRequester`.

When `database.enabled` is `false` (the default), stats, history, and leaderboard data are not persisted.

### Debug

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `debug` | boolean | `false` | Verbose debug logging (logs Velocity messages, identifier mappings, GUI events, challenge lifecycle) |

---

### GUI (`gui.yml`)

Generated at `plugins/PvPIndexBattles/gui.yml` on first run. Controls every aspect of the battle GUI appearance: inventory size, slot positions, materials, display names, lore text, and colours. See the file itself for a full list of keys. All colour codes use `&` prefix (e.g. `&6Gold`, `&bAqua`).

Key sections:

| Section | Controls |
|---------|----------|
| `battle_gui` | Main battle/queue GUI: size, title, slot layout, tab materials |
| `battle_gui.mode_icons` | Per-mode material overrides (e.g. `sword: IRON_SWORD`) |
| `challenge` | Challenge timeout and related settings |

---

## Velocity Plugin

Generated at `plugins/pvpindex-proxy/config.properties` on first run. A default template is bundled in the JAR and written automatically.

### Core Settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `paper_secret` | String | *(empty)* | Shared secret (must match `proxy.secret` on each Paper backend) |
| `monitored_servers` | String (CSV) | *(empty = all)* | Comma-separated server names to monitor. Leave empty to monitor all servers. |
| `debug` | boolean | `false` | Verbose debug logging. Shows message routing, challenge flow, and player list broadcasts. |

### Multi-Proxy Network (optional)

These settings enable cross-proxy communication via Redis. When `network.enabled` is `false` (the default), the plugin behaves exactly as a single-proxy setup.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `network.enabled` | boolean | `false` | Enable the multi-proxy network layer |
| `network.proxy_id` | String | `proxy-1` | Unique identifier for this proxy instance |
| `network.region` | String | `default` | Region label (informational) |
| `network.redis.host` | String | `localhost` | Redis server hostname |
| `network.redis.port` | int | `6379` | Redis server port |
| `network.redis.password` | String | *(empty)* | Redis password (leave empty for no auth) |
| `network.redis.database` | int | `0` | Redis database index |
| `network.redis.pool_size` | int | `8` | Jedis connection pool size |
| `network.reconnect_interval` | int | `5` | Seconds between Redis reconnection attempts |
| `network.message_timeout` | int | `10` | Max message age in seconds before discard |
| `network.heartbeat_interval` | int | `15` | Seconds between heartbeat broadcasts |
| `network.proxy_timeout` | int | `45` | Seconds without heartbeat before proxy marked offline |
| `network.transfer_strategy` | String | `shared_server` | Cross-proxy battle transfer mode: `transfer_packet`, `shared_server`, or `both` |
| `network.shared_battle_servers` | String (CSV) | *(empty)* | Servers shared across all proxies for battles |

See [SETUP-VELOCITY.md](SETUP-VELOCITY.md) for single-proxy setup and [SETUP-MULTI-PROXY.md](SETUP-MULTI-PROXY.md) for multi-proxy configuration.

---

## BungeeCord Plugin

Generated at `plugins/PvPIndex-Proxy/config.properties` on first run. Uses the same config format as the Velocity plugin (all keys above apply). See [SETUP-BUNGEECORD.md](SETUP-BUNGEECORD.md) for setup instructions.
