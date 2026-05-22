# Commands

## Paper Plugin

### /pvpindex
Root command for PvPIndex administration.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `reload` | `pvpindex.reload` | Reload plugin configuration |
| `verify <code>` | `pvpindex.admin` | Verify an API authentication code |
| `submissions` | `pvpindex.admin` | List pending battle submissions |
| `sync` | `pvpindex.admin` | Sync unsubmitted battles to the API |
| `retryfailed` | `pvpindex.admin` | Retry failed battle submissions |

### /battle
Player-facing battle queue and challenge commands.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| *(no args)* | `pvpindex.battle.queue` | Open the 54-slot battle mode selection GUI |
| `leave` | `pvpindex.battle.queue` | Leave the current matchmaking queue or forfeit an active battle |
| `challenge <player> [mode]` | `pvpindex.battle.queue` | Challenge a player to a duel. If mode is omitted, opens the GUI for mode selection |
| `accept <id>` | `pvpindex.battle.queue` | Accept an incoming challenge |
| `decline <id>` | `pvpindex.battle.queue` | Decline an incoming challenge |

### /pvpmod
Moderator tools for watching, replaying, reporting, and banning.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `watch <player>` | `pvpindex.mod` | Spectate a player's active battle |
| `replay <battleId>` | `pvpindex.mod` | Replay a recorded battle |
| `report <player> <reason>` | `pvpindex.mod.report` | Report a player |
| `reports [player]` | `pvpindex.mod` | View pending reports |
| `ban <player> <duration> <reason>` | `pvpindex.mod.ban` | Ban a player from PvPIndex battles |
| `unban <player>` | `pvpindex.mod.ban` | Unban a player |

### /party
Party management commands.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `create` | `pvpindex.party` | Create a new party |
| `invite <player>` | `pvpindex.party` | Invite a player to your party |
| `join <player>` | `pvpindex.party` | Join a player's party (requires invite) |
| `leave` | `pvpindex.party` | Leave your current party |
| `kick <player>` | `pvpindex.party` | Kick a player from your party (leader only) |
| `disband` | `pvpindex.party` | Disband your party (leader only) |

### /invite
Direct battle invitation.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `<player>` | `pvpindex.battle.queue` | Send a battle invitation to a player |

### /stats
View player statistics (requires database).

| Subcommand | Permission | Description |
|------------|------------|-------------|
| *(no args)* | `pvpindex.stats` | View your own stats |
| `<player> [mode]` | `pvpindex.stats` | View a player's stats, optionally filtered by mode |

### /history
View battle history (requires database).

| Subcommand | Permission | Description |
|------------|------------|-------------|
| *(no args)* | `pvpindex.stats` | View your own battle history |
| `<player>` | `pvpindex.stats` | View a player's battle history |

### /leaderboard
View leaderboards (requires database).

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `<mode> [stat]` | `pvpindex.stats` | View leaderboard for a mode, optionally filtered by stat (elo, wins, kd) |

## Velocity Plugin

### /vpvpindex
Proxy-level PvPIndex commands.

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `status` | `pvpindex.proxy.admin` | Show connected backends and active battles |
| `list` | `pvpindex.proxy.admin` | List all tracked players and their servers |
| `reload` | `pvpindex.proxy.admin` | Reload proxy plugin configuration |

## BungeeCord Plugin

### /pvpindex (on BungeeCord)
Proxy-level PvPIndex commands (aliases: `/pvi`).

| Subcommand | Permission | Description |
|------------|------------|-------------|
| *(no args)* | `pvpindex.admin` | Show plugin info |
| `network` | `pvpindex.admin` | Show multi-proxy network status (online proxies, global players/servers) |
| `reload` | `pvpindex.admin` | Reload config (requires proxy restart on BungeeCord) |

## Permissions

All permissions default to `op` unless noted otherwise.

| Permission | Default | Description |
|------------|---------|-------------|
| `pvpindex.admin` | op | Full admin access |
| `pvpindex.reload` | op | Reload configuration |
| `pvpindex.battle.queue` | true | Join matchmaking queues |
| `pvpindex.mod` | op | Moderator tools |
| `pvpindex.mod.ban` | op | Ban/unban players |
| `pvpindex.mod.ban.federated` | op | Federated cross-server bans |
| `pvpindex.mod.report` | true | Submit reports |
| `pvpindex.party` | true | Party commands (create, invite, join, leave, kick, disband) |
| `pvpindex.battle.commands.bypass` | op | Bypass command blocking during active battles |
| `pvpindex.stats` | true | View stats, history, and leaderboards |
