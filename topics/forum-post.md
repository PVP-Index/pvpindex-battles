**PvPIndex Battles** is a competitive PvP plugin for Paper, Folia, Purpur, Spigot, BungeeCord, and Velocity servers. It adds a full **1v1 duel and ranked matchmaking system** with per-mode **ELO ratings**, persistent global leaderboards, battle replays, and a moderation suite - all synced to [pvpindex.com](https://pvpindex.com) so your players' rankings persist across the entire network.

> **Supported game modes:** Crystal PvP · Sword PvP · Pot PvP · NoDebuff · Soup PvP · Axe PvP · Mace PvP · Boxing · Sumo · UHC

---

## Features

- **10 ranked game modes** - Crystal, Sword, Pot, NoDebuff, Soup, Axe, Mace, Boxing, Sumo, UHC - each with independent ELO ladders and separate leaderboards
- **1v1 matchmaking queue** - fully configurable 54-slot GUI with per-mode ELO, countdowns, and automatic arena teleportation
- **Cross-server duels** - `/battle challenge <player> [mode]` works standalone or routes through a Velocity or BungeeCord proxy
- **Network-wide tab completion** - proxy broadcasts all online players so `/battle challenge <TAB>` shows names from every backend server
- **Battle replay system** - frame-by-frame recording, reviewable in-game with `/pvpmod replay <id>`
- **Arena pool** - procedural, schematic, and world-copy generation strategies; four bundled schematics included
- **Moderation suite** - player reports, local bans, federated network-wide bans, real-time spectating
- **PlaceholderAPI integration** - ELO, rank, win/loss, queue state, and active mode placeholders for scoreboards and tab lists
- **Fully configurable messages** via `messages.yml`
- **HMAC-signed payloads** - battles are cryptographically signed before submission; the API rejects anything tampered with
- **Open ELO formula** - K=32 trust-weighted implementation published under MIT at [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)
- **Folia compatible** - runs on Folia, Paper, Purpur, Spigot, and Bukkit (1.21+)

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ (25+ for Paper API 26.1.x) |
| Paper / Folia / Purpur / Spigot | 1.21.x or 1.21.4+ (API 26.1.x) |
| Velocity or BungeeCord *(optional)* | Velocity 3.x / BungeeCord 1.21+ |
| PlaceholderAPI *(optional)* | 2.11+ |
| PvPIndex API key | Free at [pvpindex.com](https://pvpindex.com) |

---

## Installation

### Paper / Folia / Purpur / Spigot

1. Drop `PvPIndexBattles-<version>.jar` into `plugins/`.
2. Start the server, configs are generated automatically.
3. Stop the server, then add your API key to `plugins/PvPIndexBattles/config.yml`:

```yaml
api:
  api_key: "your-api-key-here"

server:
  id: "my-server"
```

4. Restart and run `/pvpindex` to confirm everything loaded correctly.

### Velocity / BungeeCord *(optional)*

Drop `PvPIndexBattles-velocity-<version>.jar` into your proxy `plugins/` folder and configure `plugins/pvpindex-battles/config.properties` with the same API key and shared secret as your backend servers.

→ Full proxy setup guide: [docs.pvpindex.com/server_owner/proxy-setup](https://docs.pvpindex.com/server_owner/proxy-setup)

---

## Commands

### `/battle`  Players

| Command | Description |
|---|---|
| `/battle` | Open the matchmaking mode selection GUI |
| `/battle challenge <player> [mode]` | Send a duel challenge |
| `/battle accept <id>` | Accept a challenge |
| `/battle decline <id>` | Decline a challenge |
| `/battle leave` | Leave the queue or forfeit an active battle |

### `/pvpindex` Admins

| Command | Permission | Description |
|---|---|---|
| `/pvpindex reload` | `pvpindex.reload` | Reload plugin config |
| `/pvpindex submissions` | `pvpindex.admin` | View pending battle submissions |
| `/pvpindex sync` | `pvpindex.admin` | Retry unsubmitted battles |
| `/pvpindex retryfailed` | `pvpindex.admin` | Retry failed API submissions |

### `/pvpmod`  Moderation

| Command | Permission | Description |
|---|---|---|
| `/pvpmod watch <player>` | `pvpindex.mod` | Spectate a live battle |
| `/pvpmod replay <id>` | `pvpindex.mod` | Play back a recorded battle |
| `/pvpmod report <player> <reason>` | `pvpindex.mod.report` | Report a player |
| `/pvpmod ban <player> <duration> <reason>` | `pvpindex.mod.ban` | Ban a player from battles |
| `/pvpmod unban <player>` | `pvpindex.mod.ban` | Unban a player |

---

## PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%pvpindex_elo%` | Overall ELO rating |
| `%pvpindex_elo_<mode>%` | ELO for a specific mode |
| `%pvpindex_rank%` | Global ladder position |
| `%pvpindex_wins%` | Session wins |
| `%pvpindex_losses%` | Session losses |
| `%pvpindex_kd%` | Win/loss ratio |
| `%pvpindex_in_battle%` | `true` / `false` |
| `%pvpindex_queued%` | `true` / `false` |
| `%pvpindex_queued_mode%` | Current queue mode or `none` |
| `%pvpindex_battle_type%` | Display name of the active mode |

---

## Configuration

```yaml
# plugins/PvPIndexBattles/config.yml
api:
  api_key: ""
  base_url: "https://api.pvpindex.com/api"

server:
  id: "my-server"
  require_signature: false   # Recommended: set to true in production
  debug: false

arena:
  world_prefix: "pvpindex_arena"
  cleanup_delay_ticks: 100

queue:
  max_wait_seconds: 120
```

→ Full reference: [docs.pvpindex.com/server_owner/configuration](https://docs.pvpindex.com/server_owner/configuration)

---

## Links

- **Website & leaderboards** - [pvpindex.com](https://pvpindex.com)
- **Documentation** - [docs.pvpindex.com](https://docs.pvpindex.com)
- **Source (plugin)** - [github.com/PVP-Index/pvpindex-battles](https://github.com/PVP-Index/pvpindex-battles)
- **Source (ELO formula / anti-cheat)** - [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)
- **Issue tracker** - [github.com/PVP-Index/pvpindex-battles/issues](https://github.com/PVP-Index/pvpindex-battles/issues)

---

## Licence

The ELO validator is published under [MIT](https://github.com/PVP-Index/pvpindex-battles/blob/main/LICENSE). See [LICENSE](https://github.com/PVP-Index/pvpindex-battles/blob/main/LICENSE) for the plugin itself.
