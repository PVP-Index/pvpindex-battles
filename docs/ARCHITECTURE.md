# Architecture

PvPIndex Battles is a multi-module Maven project targeting Paper (Minecraft) and Velocity (proxy).

## Module Map

```
pvpindex-parent
├── common/                   Shared data models, messaging protocol. Zero platform imports.
├── api/                      Platform-agnostic interfaces (PlatformPlugin, PluginInfo, PlatformType).
├── network/                  Cross-proxy messaging API (MessageBus, NetworkRouter, PlayerRegistry,
│                             ServerRegistry) and Redis/Jedis implementation. No platform imports.
├── platform-paper/           Shared Paper adapter code: services, listeners, commands, events.
│                             Compiles against Paper 1.21.x but never references version-specific constants.
├── paper-versions/
│   ├── paper.1.21.x/         VersionAdapter for Paper 1.21.x (Registry.*, Attribute.GENERIC_MAX_HEALTH).
│   └── paper.26.1.x/         VersionAdapter for Paper API 26.1.x (RegistryAccess, Attribute.MAX_HEALTH).
├── platform-velocity/        Velocity proxy plugin logic: listeners, messaging, commands, transfers.
├── platform-bungeecord/      BungeeCord proxy plugin logic: listeners, messaging, commands.
├── bootstrap-paper/          Paper entrypoint. Produces the final shaded Paper JAR.
├── bootstrap-velocity/       Velocity entrypoint. Produces the final shaded Velocity JAR.
├── bootstrap-bungeecord/     BungeeCord entrypoint. Produces the final shaded BungeeCord JAR.
└── docs/                     This documentation.
```

## Dependency Flow

```
common ← api ← platform-paper ← paper.1.21.x
                                ← paper.26.1.x
                               ← bootstrap-paper (shades everything into one JAR)

common ← api ← network ← platform-velocity  ← bootstrap-velocity  (shades everything into one JAR)
                        ← platform-bungeecord ← bootstrap-bungeecord (shades everything into one JAR)
```

- Modules lower in the tree never import modules above them.
- `common`, `api`, and `network` contain no Bukkit, Paper, Velocity, or BungeeCord imports.
- `platform-paper` never imports Velocity/BungeeCord classes and vice versa.
- `network` is platform-neutral: it provides interfaces and Redis implementations that both Velocity and BungeeCord platforms use.
- Version-specific modules (`paper.1.21.x`, `paper.26.1.x`) only contain adapter implementations.

## Multi-Proxy Networking

When `network.enabled=true`, each proxy connects to Redis and communicates with all other proxies:

```
                    ┌─────────┐
                    │  Redis  │
                    └────┬────┘
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │Velocity  │ │Velocity  │ │BungeeCord│
        │ US-East  │ │ EU-West  │ │  Asia    │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
          backends      backends      backends
```

Key components in the `network/` module:
- **MessageBus** - Pub/Sub interface (Redis implementation: `RedisMessageBus`)
- **NetworkRouter** - Handles proxy registration, heartbeats, timeouts, message routing
- **PlayerRegistry** - Tracks every player's location across all proxies
- **ServerRegistry** - Tracks which servers are on which proxy
- **MessageDeduplicator** - Bounded LRU set with TTL to prevent duplicate message processing

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
| `PvPIndexBattles-1.0.1.jar` | `bootstrap-paper/target/` |
| `PvPIndexBattles-velocity-1.0.1.jar` | `bootstrap-velocity/target/` |
| `PvPIndexBattles-bungeecord-1.0.1.jar` | `bootstrap-bungeecord/target/` |

Jackson is relocated per-platform (`com.pvpindex.shade.jackson`, `com.pvpindex.velocity.shade.jackson`, `com.pvpindex.bungee.shade.jackson`). Jedis is relocated similarly to avoid classpath conflicts with other plugins.

> **JDK note:** Paper API 26.1.x ships class files compiled with Java 25. JDK 25+ is required to compile the `paper.26.1.x` adapter. All other modules target Java 21 bytecode, so the final Paper JAR runs on Java 21+ servers (1.21.x) and Java 25+ servers (26.1.x).

## Challenge System

The cross-server challenge system lets players issue `/battle challenge <player> [mode]` from any backend server:

1. **Proxy mode** (Velocity connected): Challenge is serialised as `CHALLENGE_SEND` via the `pvpindex:proxy` plugin channel. Velocity resolves the target player's backend and forwards `CHALLENGE_FORWARD` to the target's server. The target receives a clickable chat message with [Accept] and [Decline] buttons. Accept/decline responses route back through Velocity. For same-server challenges, Velocity sends `CHALLENGE_CONFIRMED` to the single server. For cross-server challenges, Velocity sends `CHALLENGE_CONFIRMED` to the challenger's (hosting) server and `CHALLENGE_CLEANUP` to the target's server, then transfers the target player. The hosting server waits for the target to arrive (via `ChallengeArrivalListener`) with a stabilisation delay before starting the battle. Pending challenges expire after 30 seconds.

2. **Standalone mode** (no proxy): Challenges are resolved locally on the same server. The `ChallengeManager` directly presents the chat UI and starts the battle via `BattleQueueService.startDirect()` on acceptance.

Key classes:
- `common/.../messaging/MessageType` -- `CHALLENGE_SEND`, `CHALLENGE_ACCEPT`, `CHALLENGE_DECLINE`, `CHALLENGE_FORWARD`, `CHALLENGE_CONFIRMED`, `CHALLENGE_REJECTED`, `CHALLENGE_CLEANUP`, `NETWORK_PLAYER_LIST`
- `platform-velocity/.../challenge/PendingChallenge` -- tracks in-flight challenges on the proxy
- `platform-velocity/.../messaging/ProxyMessageHandler` -- routes challenge messages, handles cross-server transfers
- `platform-paper/.../challenge/ChallengeManager` -- Paper-side challenge lifecycle (send, accept, decline, start)
- `platform-paper/.../challenge/ChallengeArrivalListener` -- waits for transferred players to arrive and stabilise before starting the battle
- `platform-paper/.../messaging/NetworkPlayerCache` -- caches network-wide player list from Velocity for tab completion

## WorldIdentifier & WorldNormalizer

`WorldIdentifier` (record in `common/`) maps raw mode IDs (e.g. `"sword"`) to display names (e.g. `"Sword Duel"`). `WorldNormalizer` is the registry, auto-populated from `GameModeDefinition` entries at startup. Used by GUI, placeholders, commands, and Velocity messages. Never used in API payload code.

## Game Modes

The plugin ships 10 fully playable game modes out of the box, all with procedural arenas (no asset files needed):

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
