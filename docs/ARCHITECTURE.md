# Architecture

PvPIndex Battles is a multi-module Maven project targeting Paper (Minecraft), Velocity (proxy), and BungeeCord (proxy).

As of 1.0.2, the architecture is **lobby-centric**: Paper lobby servers connect directly to Redis for global features (player sync, challenges, presence, invites, parties, routing), whilst proxies are simplified to auth, routing, and transfers only. An optional database layer provides persistent storage for stats, history, and leaderboards.

## Module Map

```
pvpindex-parent
├── common/                   Shared data models, messaging protocol. Zero platform imports.
├── api/                      Platform-agnostic interfaces (PlatformPlugin, PluginInfo, PlatformType).
├── network/                  Cross-proxy messaging API (MessageBus, NetworkRouter, PlayerRegistry,
│                             ServerRegistry) and Redis/Jedis implementation. No platform imports.
├── database/                 Optional persistent storage layer (MySQL, SQLite, MongoDB).
│                             Platform-neutral. Provides DataService interface and implementations.
├── platform-paper/           Shared Paper adapter code: services, listeners, commands, events.
│   ├── network/              Lobby-mode Redis services: LobbyNetworkService, PlayerSyncService,
│   │                         ChallengeSyncService, PresenceService, InviteService, PartySyncService,
│   │                         RoutingService, TransferRequester.
│   └── data/                 DataService integration and PlayerCache.
├── paper-versions/
│   ├── paper.1.21.x/         VersionAdapter for Paper 1.21.x (Registry.*, Attribute.GENERIC_MAX_HEALTH).
│   └── paper.26.1.x/         VersionAdapter for Paper API 26.1.x (RegistryAccess, Attribute.MAX_HEALTH).
├── platform-velocity/        Velocity proxy plugin logic: auth, routing, transfers.
├── platform-bungeecord/      BungeeCord proxy plugin logic: auth, routing, transfers.
├── bootstrap-paper/          Paper entrypoint. Produces the final shaded Paper JAR.
├── bootstrap-velocity/       Velocity entrypoint. Produces the final shaded Velocity JAR.
├── bootstrap-bungeecord/     BungeeCord entrypoint. Produces the final shaded BungeeCord JAR.
└── docs/                     This documentation.
```

## Dependency Flow

```
common ← api ← database (optional)
             ← platform-paper ← paper.1.21.x
                               ← paper.26.1.x
                               ← bootstrap-paper (shades everything into one JAR)

common ← api ← network ← platform-velocity  ← bootstrap-velocity  (shades everything into one JAR)
                        ← platform-bungeecord ← bootstrap-bungeecord (shades everything into one JAR)
```

- Modules lower in the tree never import modules above them.
- `common`, `api`, `network`, and `database` contain no Bukkit, Paper, Velocity, or BungeeCord imports.
- `platform-paper` never imports Velocity/BungeeCord classes and vice versa.
- `network` is platform-neutral: it provides interfaces and Redis implementations that both Velocity and BungeeCord platforms use.
- `database` is platform-neutral: it provides `DataService` interface and storage implementations (MySQL, SQLite, MongoDB).
- `platform-paper` now embeds a `network/` package for lobby-mode Redis services and a `data/` package for `DataService` integration and `PlayerCache`.
- Version-specific modules (`paper.1.21.x`, `paper.26.1.x`) only contain adapter implementations.

## Server Modes

Paper servers operate in one of two modes, controlled by `lobby.enabled` in `config.yml`:

| Mode | `lobby.enabled` | Description |
|------|-----------------|-------------|
| **Lobby** | `true` | Connects directly to Redis. Provides global player list, cross-server challenges, presence tracking, invites, parties, and routing. The lobby is the primary coordinator for network features. |
| **Backend / SMP** | `false` (default) | Runs as a standalone battle server or behind a proxy. Receives routed players and hosts battles. |

Proxies (Velocity/BungeeCord) are simplified in 1.0.2 to handle:
- Player authentication and forwarding
- Server routing and connection management
- Transfer packets for cross-server player movement

All global state synchronisation (player lists, challenges, presence, invites, parties) is handled by lobby Paper servers via Redis, not by proxies.

## Data Flow

```
                ┌───────────────┐
                │    Redis      │   Real-time state (Pub/Sub + key/value)
                │               │   Player presence, challenges, invites,
                │               │   parties, routing, player sync
                └──────┬────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  Lobby   │ │  Lobby   │ │ Backend  │
    │ Paper #1 │ │ Paper #2 │ │ Paper #3 │
    └────┬─────┘ └────┬─────┘ └────┬─────┘
         │             │            │
         └─────────────┼────────────┘
                       ▼
                ┌──────────────┐
                │   Database   │   Persistent storage (optional)
                │ MySQL/SQLite │   Player stats, battle history,
                │  /MongoDB    │   leaderboards
                └──────────────┘
```

Three data layers:
1. **Redis (real-time)**: Pub/Sub channels and key/value storage for live state. Used by lobby-mode services (`PlayerSyncService`, `ChallengeSyncService`, `PresenceService`, `InviteService`, `PartySyncService`, `RoutingService`).
2. **Database (persistent)**: Optional MySQL, SQLite, or MongoDB backend for player stats, battle history, and leaderboard data. Managed by `DataService` in the `database/` module.
3. **PlayerCache (in-memory)**: Local cache on each Paper server, populated from Redis and database queries. Provides fast lookups without repeated external calls.

## Multi-Proxy Networking

When `network.enabled=true` on a proxy, it connects to Redis for cross-proxy coordination. Lobby servers also connect to this same Redis instance for global features.

```
                    ┌─────────┐
                    │  Redis  │
                    └────┬────┘
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │Velocity  │    │Velocity  │    │BungeeCord│
   │ US-East  │    │ EU-West  │    │  Asia    │
   └────┬─────┘    └────┬─────┘    └────┬─────┘
     backends        backends        backends
   (lobby + SMP)   (lobby + SMP)   (lobby + SMP)
```

Proxies handle auth, routing, and transfers. Lobby Paper servers handle global sync (player lists, challenges, presence, invites, parties) directly via Redis.

Key components in the `network/` module:
- **MessageBus** - Pub/Sub interface (Redis implementation: `RedisMessageBus`)
- **NetworkRouter** - Handles proxy registration, heartbeats, timeouts, message routing
- **PlayerRegistry** - Tracks every player's location across all proxies
- **ServerRegistry** - Tracks which servers are on which proxy
- **MessageDeduplicator** - Bounded LRU set with TTL to prevent duplicate message processing

Key services in `platform-paper/network/` (lobby mode):
- **LobbyNetworkService** - Orchestrates all lobby-mode Redis services
- **PlayerSyncService** - Synchronises player lists across all lobby servers
- **ChallengeSyncService** - Routes and tracks cross-server challenges via Redis
- **PresenceService** - Tracks player online/offline presence globally
- **InviteService** - Manages cross-server battle invitations
- **PartySyncService** - Synchronises party state (create, invite, join, leave, kick, disband)
- **RoutingService** - Determines which backend server to route players to
- **TransferRequester** - Requests player transfers via the proxy

## Version Detection

At startup, `bootstrap-paper` runs `resolveVersionAdapter()`:

1. Checks for `io.papermc.paper.registry.RegistryAccess` on the classpath (Paper 26.1.x).
2. Falls back to `org.bukkit.Registry` (Paper 1.21.x).
3. Instantiates the matching `VersionAdapter` reflectively to avoid loading classes that do not exist on the running server.
4. If neither is found, the plugin disables itself with a clear error message.

The adapter is passed to every service that touches version-specific API surface: `PlayerStateService`, `BattleQueueService`, `KitApplier`.

## Build

```bash
mvn clean package     # requires JDK 25+ for full build
```

Produces three fat JARs:

| Artefact | Location |
|----------|----------|
| `PvPIndexBattles-1.0.2.jar` | `bootstrap-paper/target/` |
| `PvPIndexBattles-velocity-1.0.2.jar` | `bootstrap-velocity/target/` |
| `PvPIndexBattles-bungeecord-1.0.2.jar` | `bootstrap-bungeecord/target/` |

Jackson is relocated per-platform (`com.pvpindex.shade.jackson`, `com.pvpindex.velocity.shade.jackson`, `com.pvpindex.bungee.shade.jackson`). Jedis is relocated similarly to avoid classpath conflicts with other plugins.

> **JDK note:** Paper API 26.1.x ships class files compiled with Java 25. JDK 25+ is required to compile the `paper.26.1.x` adapter. All other modules target Java 21 bytecode, so the final Paper JAR runs on Java 21+ servers (1.21.x) and Java 25+ servers (26.1.x).

## Challenge System

The cross-server challenge system lets players issue `/battle challenge <player> [mode]` from any backend server:

1. **Lobby mode** (Redis connected): The lobby server's `ChallengeSyncService` publishes the challenge to Redis. The target player's lobby server receives it and presents a clickable chat message with [Accept] and [Decline] buttons. Accept/decline responses route back through Redis. On acceptance, `RoutingService` determines the hosting server and `TransferRequester` moves the target player. The hosting server waits for the target to arrive (via `ChallengeArrivalListener`) with a stabilisation delay before starting the battle. Pending challenges expire after 30 seconds.

2. **Proxy mode** (Velocity/BungeeCord connected, no lobby): Challenge is serialised as `CHALLENGE_SEND` via the `pvpindex:proxy` plugin channel. The proxy resolves the target player's backend and forwards `CHALLENGE_FORWARD` to the target's server. Accept/decline responses route back through the proxy. For cross-server challenges, the proxy sends `CHALLENGE_CONFIRMED` to the challenger's server and transfers the target player.

3. **Standalone mode** (no proxy, no lobby): Challenges are resolved locally on the same server. The `ChallengeManager` directly presents the chat UI and starts the battle via `BattleQueueService.startDirect()` on acceptance.

Key classes:
- `common/.../messaging/MessageType` -- `CHALLENGE_SEND`, `CHALLENGE_ACCEPT`, `CHALLENGE_DECLINE`, `CHALLENGE_FORWARD`, `CHALLENGE_CONFIRMED`, `CHALLENGE_REJECTED`, `CHALLENGE_CLEANUP`, `NETWORK_PLAYER_LIST`
- `platform-velocity/.../messaging/ProxyMessageHandler` -- routes challenge messages, handles cross-server transfers, tracks legacy challenges via `PendingLegacyChallenge`
- `platform-paper/.../challenge/ChallengeManager` -- Paper-side challenge lifecycle (send, accept, decline, start)
- `platform-paper/.../challenge/ChallengeArrivalListener` -- waits for transferred players to arrive and stabilise before starting the battle
- `platform-paper/.../messaging/NetworkPlayerCache` -- caches network-wide player list from Redis and proxy broadcasts for tab completion. In lobby mode, merges proxy updates as a seed whilst Redis events remain authoritative.
- `platform-paper/.../network/ChallengeSyncService` -- lobby-mode challenge routing via Redis
- `platform-paper/.../network/RoutingService` -- determines target server for battle routing
- `platform-paper/.../network/TransferRequester` -- requests player transfers via the proxy

## WorldIdentifier & WorldNormalizer

`WorldIdentifier` (record in `common/`) maps raw mode IDs (e.g. `"sword"`) to display names (e.g. `"Sword Duel"`). `WorldNormalizer` is the registry, auto-populated from `GameModeDefinition` entries at startup. Used by GUI, placeholders, commands, and Velocity messages. Never used in API payload code.

## Game Modes

The plugin ships 11 fully playable game modes out of the box, all with procedural arenas (no asset files needed):

| Mode | Arena Strategy | Description |
|------|---------------|-------------|
| Sword | `procedural` | Classic iron sword 1v1 |
| Pot | `procedural` | Splash potion PvP |
| NoDebuff | `procedural` | Healing pots only, permanent Speed/Strength |
| Soup | `procedural` | Mushroom stew instant healing |
| Axe | `procedural` | 1.9+ axe and shield combat |
| Mace | `procedural` | 1.21 mace with wind charges |
| Boxing | `procedural` | Bare fists, no armour |
| Sumo | `procedural_sumo` | Knockback stick on elevated platform |
| Crystal | `procedural_crystal` | End crystals on obsidian floor |
| UHC | `procedural` | Ultra Hardcore duel |
| SMP | `procedural` | Survival multiplayer duel |
