# PvPIndex Battles Verified ELO Tracking for Competitive PvP

**PvPIndex Battles** connects your Paper or Velocity server to [pvpindex.com](https://pvpindex.com) — an open, tamper-proof ELO ranking platform for competitive Minecraft PvP.

Every duel is recorded, cryptographically signed, and submitted to the API. Players earn verified ratings that persist across your network and stack up against the global leaderboard.

---

## Features

- **10 built-in game modes**  Sword, Pot, NoDebuff, Soup, Axe, Mace, Boxing, Sumo, Crystal, UHC
- **Matchmaking queue**  fully configurable 54-slot GUI with per-mode ELO, countdowns, and automatic arena teleportation
- **Cross-server challenges**  `/battle challenge <player> [mode]` works standalone or routes through a Velocity proxy
- **Network-wide tab completion**  Velocity broadcasts online players across all backends, so `/battle challenge <TAB>` always shows the full network
- **Replay system**  frame-by-frame battle recording, playable in-game with `/pvpmod replay <id>`
- **Arena pool**  procedural, schematic, and world-copy generation; four schematics included out of the box
- **Moderation suite**  player reports, local bans, federated network-wide bans, and real-time spectating
- **PlaceholderAPI support**  ELO, rank, win/loss, queue state, and active mode placeholders
- **Configurable messages**  full control over all player-facing text via `messages.yml`
- **HMAC-signed payloads**  battles are signed before submission; the API rejects anything that doesn't match
- **Open ELO formula**  K=32 trust-weighted implementation, published under MIT at [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ (25+ for Paper API 26.1.x) |
| Paper | 1.21.x or 1.21.4+ (API 26.1.x) |
| Velocity *(optional)* | 3.x |
| PlaceholderAPI *(optional)* | 2.11+ |
| PvPIndex API key | Free at [pvpindex.com](https://pvpindex.com) |

---

## Installation

### Paper

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

### Velocity *(optional)*

Drop `PvPIndexBattles-velocity-<version>.jar` into your Velocity `plugins/` folder and configure `plugins/pvpindex-battles/config.properties` with the same API key and shared secret as your Paper backends.

→ See [SETUP-VELOCITY.md](../docs/SETUP-VELOCITY.md) for the full proxy setup guide.

---

## Commands

### `/battle` — Players

| Command | Description |
|---|---|
| `/battle` | Open the matchmaking mode selection GUI |
| `/battle challenge <player> [mode]` | Send a duel challenge |
| `/battle accept <id>` | Accept a challenge |
| `/battle decline <id>` | Decline a challenge |
| `/battle leave` | Leave the queue or forfeit an active battle |

### `/pvpindex` — Admins

| Command | Permission | Description |
|---|---|---|
| `/pvpindex reload` | `pvpindex.reload` | Reload plugin config |
| `/pvpindex submissions` | `pvpindex.admin` | View pending battle submissions |
| `/pvpindex sync` | `pvpindex.admin` | Retry unsubmitted battles |
| `/pvpindex retryfailed` | `pvpindex.admin` | Retry failed API submissions |

### `/pvpmod` — Moderation

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

→ Full reference: [CONFIGURATION.md](../docs/CONFIGURATION.md)

---

## Links

- **Website & leaderboards**  [pvpindex.com](https://pvpindex.com)
- **Source (plugin)**  [github.com/PVP-Index/plugin](https://github.com/PVP-Index/plugin)
- **Source (ELO validator)**  [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)
- **Issue tracker**  [github.com/PVP-Index/plugin/issues](https://github.com/PVP-Index/plugin/issues)

---

## License

The ELO validator is published under [MIT](../LICENSE). See [LICENSE](../LICENSE) for the plugin itself.
