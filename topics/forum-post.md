# PvPIndex Battles — Verified ELO Tracking for Competitive PvP

**PvPIndex Battles** connects your Paper or Velocity server to [pvpindex.com](https://pvpindex.com) — an open, tamper-proof ELO ranking platform for competitive Minecraft PvP. Every duel gets recorded, signed, and submitted to the API; your players earn verified ratings they can compare against the rest of the network.

---

## Features

- **10 built-in game modes** — Sword, Pot, NoDebuff, Soup, Axe, Mace, Boxing, Sumo, Crystal, UHC
- **Matchmaking queue** with a fully configurable 54-slot GUI (`gui.yml`), per-mode ELO, countdowns, and arena teleportation
- **Cross-server challenges** — `/battle challenge <player> [mode]` works standalone or routes through a connected Velocity proxy
- **Network-wide tab completion** — Velocity broadcasts all online players so `/battle challenge <TAB>` shows names from every backend server
- **Replay system** — frame-by-frame battle recording with in-game `/pvpmod replay <id>` playback
- **Arena pool** — procedural, schematic, and world-copy generation strategies; four bundled schematics included
- **Moderation suite** — reports, local bans, federated network-wide bans, real-time spectating
- **PlaceholderAPI support** — ELO, rank, win/loss, battle state, and battle type placeholders
- **Fully configurable messages** via `messages.yml`
- **HMAC-signed payloads** — battles are cryptographically signed before transmission so the server can't fake results
- **Open ELO formula** — K=32, trust-weighted implementation published under MIT at [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ (25+ for Paper 26.1.x servers) |
| Paper | 1.21.x **or** 1.21.4+ (API 26.1.x) |
| Velocity *(optional)* | 3.x |
| PlaceholderAPI *(optional)* | 2.11+ |
| PvPIndex API key | Free at [pvpindex.com](https://pvpindex.com) |

---

## Installation

### Paper

1. Drop `PvPIndexBattles-<version>.jar` into your server's `plugins/` folder.
2. Start the server — config files are generated automatically.
3. Stop the server and set your API key in `plugins/PvPIndexBattles/config.yml`:

```yaml
api:
  api_key: "your-api-key-here"

server:
  id: "my-server"
```

4. Restart and run `/pvpindex` to confirm the plugin loaded correctly.

### Velocity (optional)

Drop `PvPIndexBattles-velocity-<version>.jar` into your Velocity `plugins/` folder and configure `plugins/pvpindex-battles/config.properties` with the same API key and shared secret used on your Paper backends.

See [SETUP-VELOCITY.md](../docs/SETUP-VELOCITY.md) for the full proxy setup guide.

---

## Commands

### `/battle` — Player commands
| Command | Description |
|---|---|
| `/battle` | Open the matchmaking mode selection GUI |
| `/battle challenge <player> [mode]` | Challenge another player to a duel |
| `/battle accept <id>` | Accept an incoming challenge |
| `/battle decline <id>` | Decline an incoming challenge |
| `/battle leave` | Leave the queue or forfeit an active battle |

### `/pvpindex` — Admin commands
| Command | Permission | Description |
|---|---|---|
| `/pvpindex reload` | `pvpindex.reload` | Reload plugin configuration |
| `/pvpindex submissions` | `pvpindex.admin` | List pending battle submissions |
| `/pvpindex sync` | `pvpindex.admin` | Retry unsubmitted battles |
| `/pvpindex retryfailed` | `pvpindex.admin` | Retry failed API submissions |

### `/pvpmod` — Moderation commands
| Command | Permission | Description |
|---|---|---|
| `/pvpmod watch <player>` | `pvpindex.mod` | Spectate a live battle |
| `/pvpmod replay <id>` | `pvpindex.mod` | Play back a recorded battle |
| `/pvpmod report <player> <reason>` | `pvpindex.mod.report` | Report a player |
| `/pvpmod ban <player> <duration> <reason>` | `pvpindex.mod.ban` | Ban a player from PvPIndex battles |
| `/pvpmod unban <player>` | `pvpindex.mod.ban` | Unban a player |

---

## PlaceholderAPI Placeholders

| Placeholder | Description |
|---|---|
| `%pvpindex_elo%` | Overall ELO rating |
| `%pvpindex_elo_<mode>%` | ELO for a specific game mode |
| `%pvpindex_rank%` | Overall ladder rank position |
| `%pvpindex_wins%` | Session win count |
| `%pvpindex_losses%` | Session loss count |
| `%pvpindex_kd%` | Win/loss ratio |
| `%pvpindex_in_battle%` | `true` / `false` |
| `%pvpindex_queued%` | `true` / `false` |
| `%pvpindex_queued_mode%` | Mode ID or `none` |
| `%pvpindex_battle_type%` | Display name of active game mode |

---

## Configuration Snapshot

```yaml
# plugins/PvPIndexBattles/config.yml
api:
  api_key: ""
  base_url: "https://api.pvpindex.com/api"

server:
  id: "my-server"
  require_signature: false      # Set true to enforce HMAC signing (recommended)
  debug: false

arena:
  world_prefix: "pvpindex_arena"
  cleanup_delay_ticks: 100

queue:
  max_wait_seconds: 120
```

Full reference: [CONFIGURATION.md](../docs/CONFIGURATION.md)

---

## Links

- **Website & leaderboards** — [pvpindex.com](https://pvpindex.com)
- **Modrinth** — [modrinth.com/project/pvpindex-battle](https://modrinth.com/project/pvpindex-battle)
- **Source (plugin)** — [github.com/PVP-Index/plugin](https://github.com/PVP-Index/plugin)
- **Source (ELO formula / anti-cheat)** — [github.com/PVP-Index/battle-validator](https://github.com/PVP-Index/battle-validator)
- **Issue tracker** — [github.com/PVP-Index/plugin/issues](https://github.com/PVP-Index/plugin/issues)

---

## Licence

[MIT](../LICENSE) for the open-source validator package.  
See [LICENSE](../LICENSE) for the plugin itself.
