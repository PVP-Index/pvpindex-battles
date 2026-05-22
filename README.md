# PvPIndex Battles

Competitive PvP battle tracking, replay recording, Elo rating, moderation, and cross-server coordination for Minecraft. Ships as three separate JARs: one for **Paper** servers, one for **Velocity** proxies, and one for **BungeeCord** proxies. Each proxy JAR is independent, install only the one matching your proxy platform.

Paper servers can run in **lobby mode** (connecting directly to Redis for global features) or **backend/SMP mode** (the default). Proxies handle auth, routing, and transfers only.

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 21+ (25+ for Paper 26.1.x servers) |
| Paper | 1.21.x or 1.21.4+ (API 26.1.x) |
| Velocity (optional) | 3.x |
| BungeeCord (optional) | 1.21+ |
| Redis (optional) | 6.x+ (required for lobby mode and multi-proxy networks) |
| Database (optional) | MySQL 8+, SQLite, or MongoDB 6+ (persistent stats/history) |
| PlaceholderAPI (optional) | 2.11+ |

## Quick Start

```bash
git clone https://github.com/PVP-Index/plugin.git
cd plugin
mvn clean package
```

Output:

- `bootstrap-paper/target/PvPIndexBattles-1.0.4.jar` - drop into Paper `plugins/`
- `bootstrap-velocity/target/PvPIndexBattles-velocity-1.0.4.jar` - drop into Velocity `plugins/`
- `bootstrap-bungeecord/target/PvPIndexBattles-bungeecord-1.0.4.jar` - drop into BungeeCord `plugins/`

The Paper JAR auto-detects your server version (1.21.x or 26.1.x) at startup. No manual configuration needed for version selection.

## Documentation

| Document | Contents |
|----------|----------|
| [Setup Guide: Paper](docs/SETUP-PAPER.md) | Installing, configuring, and verifying the Paper plugin |
| [Setup Guide: Velocity](docs/SETUP-VELOCITY.md) | Proxy setup, linking backends, shared secrets |
| [Setup Guide: BungeeCord](docs/SETUP-BUNGEECORD.md) | BungeeCord proxy setup and configuration |
| [Multi-Proxy Setup](docs/SETUP-MULTI-PROXY.md) | Running multiple proxies with Redis |
| [Architecture](docs/ARCHITECTURE.md) | Module map, dependency flow, version detection |
| [Configuration](docs/CONFIGURATION.md) | Full config.yml and config.properties reference |
| [Commands](docs/COMMANDS.md) | All commands and permissions for Paper, Velocity, and BungeeCord |
| [Version Support](docs/VERSIONING.md) | Supported Paper versions, what changed, adding new versions |
| [Development](docs/DEVELOPMENT.md) | Building, testing, code style, contributing |

## Modules

```
pvpindex-parent
├── common/                  Shared data models, messaging protocol
├── api/                     Platform-agnostic interfaces
├── network/                 Cross-proxy messaging API and Redis implementation
├── database/                Optional persistent storage (MySQL, SQLite, MongoDB)
├── platform-paper/          Paper services, listeners, commands, events
│   ├── network/             Lobby-mode Redis services (sync, presence, invites, parties, routing)
│   └── data/                DataService, PlayerCache
├── paper-versions/
│   ├── paper.1.21.x/        Version adapter for 1.21.x
│   └── paper.26.1.x/        Version adapter for 26.1.x
├── platform-velocity/       Velocity proxy plugin logic
├── platform-bungeecord/     BungeeCord proxy plugin logic
├── bootstrap-paper/         Paper entrypoint (produces PvPIndexBattles.jar)
├── bootstrap-velocity/      Velocity entrypoint (produces PvPIndexBattles-velocity.jar)
└── bootstrap-bungeecord/    BungeeCord entrypoint (produces PvPIndexBattles-bungeecord.jar)
```

## Features

- **11 game modes** out of the box: Sword, Pot, NoDebuff, Soup, Axe, Mace, Boxing, Sumo, Crystal, UHC, SMP
- **Matchmaking queue** with configurable GUI (`gui.yml`), per-mode Elo, countdowns, and arena teleportation
- **Cross-server challenges**: `/battle challenge <player> [mode]` routes through proxy or lobby Redis, or works standalone
- **Lobby mode**: Paper lobby servers connect directly to Redis for global player lists, challenges, presence, invites, parties, and routing without relying on a proxy
- **Network-wide tab completion**: lobby Redis sync or proxy broadcast ensures `/battle challenge <TAB>` shows players on every server
- **Challenge UI**: clickable chat accept/decline buttons with clear feedback messages
- **Procedural arenas**: duel, crystal, and sumo arenas generated in code with no asset files
- **Replay system** with frame-by-frame recording and in-game playback
- **Arena pool** with procedural, schematic, and world-copy generation strategies
- **Moderation** with reports, local bans, and federated network-wide bans
- **Party system** with create, invite, join, leave, kick, and disband
- **Persistent database** support (MySQL, SQLite, MongoDB) for player stats, battle history, and leaderboards
- **Stats and leaderboards**: `/stats`, `/history`, `/leaderboard` commands with per-mode breakdowns
- **Cross-server coordination** via lobby Redis or proxy plugin messaging with visible error logging
- **Multi-proxy networking** via Redis Pub/Sub for unlimited proxy instances across regions
- **PlaceholderAPI** integration for Elo, rank, win/loss, battle state, and battle type
- **Configurable GUI** via `gui.yml` for full customisation of materials, slots, titles, and colours
- **WorldNormalizer** display layer mapping raw mode IDs to friendly names
- **Multi-language support** (English, Dutch, German, Polish, Chinese, Spanish) with custom language file support
- **Command blocking** during battles with configurable whitelist and bypass permission
- **Debug logging** system gated behind config flag
- **Player state snapshots** with crash recovery and cross-server transfer stabilisation
- **Custom Bukkit events** for developer integration

## Licence

See [LICENSE](LICENSE) for details.
