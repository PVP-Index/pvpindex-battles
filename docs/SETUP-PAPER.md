# Setup Guide: Paper

## Prerequisites

- A Paper server running Minecraft **1.21.x** or **1.21.4+**
- Java **21+** for Paper 1.21.x servers, or Java **25+** for Paper 26.1.x servers

## Step 1: Install

1. Download or build `PvPIndexBattles-1.1.0.jar`.
2. Place it in your server's `plugins/` folder.

## Step 2: First Run

Start the server. The plugin generates its config files automatically:

```
plugins/PvPIndexBattles/
├── config.yml          Main configuration
├── gamemodes.yml       Game mode definitions
├── arenas.yml          Arena definitions
├── templates.yml       World template config
├── schematics.yml      Schematic spawn metadata
├── lang/               Per-language message files
│   ├── en.yml          English (default)
│   ├── nl.yml          Dutch
│   ├── de.yml          German
│   ├── pl.yml          Polish
│   ├── zh.yml          Chinese (Simplified)
│   └── es.yml          Spanish
└── schematics/         Bundled arena schematics
    ├── arena.schem
    ├── colosseum.schem
    ├── pvparena.schem
    └── royal.schem
```

To change the language, set `language: "nl"` (or any other code) in `config.yml` and run `/pvpindex reload`.

Check the console for the PVP INDEX ASCII art banner (Green-Yellow-Red gradient) followed by:

```
[PvPIndexBattles] Minecraft version: 1.21.x
```

The startup summary shows the detected adapter (`Paper121VersionAdapter` or `Paper2610VersionAdapter` on 1.21.4+ servers), game mode count, proxy status, and debug state.

## Step 3: Configure

Stop the server and edit `plugins/PvPIndexBattles/config.yml`:

```yaml
api:
  api_key: "your-api-key-here"    # Get one at pvpindex.com

server:
  id: "my-server"                 # Unique name for this server
```

See [CONFIGURATION.md](CONFIGURATION.md) for the full reference.

## Step 4: Verify

1. Start the server and join.
2. Run `/pvpindex` to check plugin status.
3. Run `/battle` to open the matchmaking GUI.
4. Check the console for any errors.

## Optional: PlaceholderAPI

1. Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).
2. Restart. PvPIndex registers its expansion automatically.
3. Available placeholders:

| Placeholder | Description |
|-------------|-------------|
| `%pvpindex_elo%` | Overall Elo rating |
| `%pvpindex_elo_<mode>%` | Elo for a specific mode |
| `%pvpindex_rank%` | Overall ladder rank |
| `%pvpindex_in_battle%` | `true`/`false` |
| `%pvpindex_queued%` | `true`/`false` |
| `%pvpindex_queued_mode%` | Mode ID or `none` |
| `%pvpindex_battle_type%` | Display name of current battle mode (or `none`) |
| `%pvpindex_battle_type_raw%` | Raw mode ID of current battle |
| `%pvpindex_battle_type_normalized%` | Alias for `battle_type` |
| `%pvpindex_battle_id%` | Mode + full battle UUID (e.g. `mace-550e8400-e29b-41d4-a716-446655440000`) |
| `%pvpindex_short_battle_id%` | Mode + first segment of battle UUID (e.g. `mace-550e8400`) |
| `%pvpindex_wins%` | Session win count |
| `%pvpindex_losses%` | Session loss count |
| `%pvpindex_kd%` | Win/loss ratio |

## Optional: Lobby Mode

Lobby mode enables direct Redis connectivity for global features (player sync, challenges, presence, invites, parties, routing) without relying on a proxy for coordination.

Edit `plugins/PvPIndexBattles/config.yml`:

```yaml
lobby:
  enabled: true
  redis:
    host: "redis.internal"
    port: 6379
    password: ""
    database: 0
    pool_size: 8
  sync_interval_ticks: 20
```

When `lobby.enabled` is `true`, the server starts `LobbyNetworkService` and all its dependent services (`PlayerSyncService`, `ChallengeSyncService`, `PresenceService`, `InviteService`, `PartySyncService`, `RoutingService`, `TransferRequester`).

When `lobby.enabled` is `false` (the default), the server runs in backend/SMP mode. No Redis connection is made from the Paper side.

See [CONFIGURATION.md](CONFIGURATION.md) for the full `lobby.*` reference.

## Optional: Database

An optional database layer provides persistent storage for player stats, battle history, and leaderboards. Supported backends: MySQL, SQLite, MongoDB.

Edit `plugins/PvPIndexBattles/config.yml`:

```yaml
database:
  enabled: true
  type: "mysql"            # mysql, sqlite, or mongodb
  mysql:
    host: "localhost"
    port: 3306
    database: "pvpindex"
    username: "pvpindex"
    password: "your-password"
    pool_size: 10
  sqlite:
    file: "pvpindex.db"
  mongodb:
    uri: "mongodb://localhost:27017"
    database: "pvpindex"
```

When `database.enabled` is `false` (the default), stats and history are not persisted. The plugin operates with in-memory/API-only data.

See [CONFIGURATION.md](CONFIGURATION.md) for the full `database.*` reference.

## Metrics (bStats)

The plugin collects anonymous usage statistics via [bStats](https://bstats.org/plugin/bukkit/PvPIndex%20Battle/31131). This includes server count, player count, Java version, and Minecraft version. No personal or gameplay data is collected.

To disable bStats globally for all plugins, edit `plugins/bStats/config.yml` and set `enabled: false`.

## Optional: Vault Economy Rewards

If [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy provider (e.g. EssentialsX, EzEconomy) are installed, battle winners receive currency rewards automatically. Edit `vault-rewards.yml` to configure per-mode amounts and optional win streak multipliers. See [CONFIGURATION.md](CONFIGURATION.md#vault-economy-rewards) for full details.

If Vault is not installed, rewards are silently disabled. No configuration changes are needed.

## Optional: Velocity Proxy

If you run a Velocity network, see [SETUP-VELOCITY.md](SETUP-VELOCITY.md) for linking this backend to the proxy.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Plugin not loading | Check you are on Paper 1.21.x or 1.21.4+. Paper 26.1.x requires Java 25+. Check console for errors. |
| `api_key: "change-me"` warning | Set your API key in config.yml |
| Battles not submitting | Run `/pvpindex submissions` then `/pvpindex retryfailed` |
| Arena worlds not cleaning up | Check `cleanup.interval_ticks` in config.yml |
| Player inventory lost after crash | Snapshots are in `plugins/PvPIndexBattles/state/`, restored on next login |
| Unsupported version error | You need Paper 1.21.x or 1.21.4+ |
