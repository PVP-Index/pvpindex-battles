# Changelog

All notable changes to PvPIndex Battles are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release tags use the `v` prefix (e.g. `v1.0.2`).

---

## [Unreleased]

### Added
### Changed
### Fixed
### Removed

---

## [1.1.1] - 2026-07-15

### Added
- Kit item support for per-item `potion_effects` in `gamemodes.yml`. Potion items can now define explicit effects using `TYPE:durationTicks:amplifier` (for example, `INSTANT_HEALTH:1:1`).
- New PlaceholderAPI placeholder `%pvpindex_battle_short_id%` returning the current game mode plus the first segment of the active battle UUID (e.g. `mace-550e8400`).
- New PlaceholderAPI placeholders `%pvpindex_battle_id%` (full battle UUID) and `%pvpindex_short_battle_id%` (first segment only).
- Configurable double-teleport to prevent `/back` commands from teleporting to the arena after battle end. When `battle_commands.prevent_back_command` is enabled (default), players are teleported twice to their restored location so `/back` points to a safe post-battle location instead of the arena.

### Fixed
- Ender pearls, chorus fruit, bows, crossbows, fire charges, and wind charges can no longer be used during the pre-battle countdown, preventing players from moving or attacking before the battle has officially started.
- Added `BattleSessionCache` for O(1) player-to-active-battle lookups, replacing linear scans in event handlers.
- Split `BattleEventListener` into focused listener classes under `com.pvpindex.battles.listener.battle` (one `@EventHandler` per class) and a thin backward-compatible facade.
- Pot PvP and NoDebuff starter kits no longer give empty splash potions. The default `SPLASH_POTION` entries in `gamemodes.yml` now include explicit potion effects (Healing II, Strength II, Speed II, and Regeneration II where applicable).
- Kit item display names and lore now translate legacy `&` colour codes when applied, so potion names like `&aHealing II` render with colours in-game instead of raw ampersand codes.
- Tipped arrows in kits now apply their configured potion effect on hit. `KitApplier` derives a base `PotionType` from the first item effect so the game recognises the arrow payload.
- Network node timeout boundary handling is now inclusive, fixing `proxyNodeTimeoutDetection` for `isTimedOut(0)` and preventing zero-second timeout edge-case mismatches.
- README build output examples now reference `1.1.1` JAR filenames to match the current Maven project version.

---

## [1.1.0] - 2026-06-06

### Added
- **Vault economy rewards** ([#15](https://github.com/PVP-Index/pvpindex-battles/issues/15)): battle winners now receive configurable currency payouts via the Vault API. Fully configured through `vault-rewards.yml` with per-game-mode base amounts and an optional win streak multiplier. Vault is a soft dependency: if not installed, rewards are silently disabled. Closes [#15](https://github.com/PVP-Index/pvpindex-battles/issues/15).
- New `vault-rewards.yml` configuration file. All settings live under a top-level `economy:` key with `enabled`, `rewards` (per-mode amounts), and `streak_multiplier` (enabled, increment, max, min_streak) sections. The bundled default ships with `economy.enabled: false` so a fresh install does not pay out until opted in.
- New `VaultRewardService` in `platform-paper` that hooks into Vault, listens for `PvPIndexBattleFinishEvent`, and deposits rewards.
- New `VaultRewardConfig` record for parsing and validating reward configuration from the `economy` section.
- New PlaceholderAPI placeholders: `%pvpindex_reward_last%` (last reward received) and `%pvpindex_streak%` (current win streak).
- New lang keys `reward.received` and `reward.received_streak` in all 6 bundled locale files (en, de, nl, es, pl, zh).
- Vault status line in the startup summary showing whether economy rewards are active.

### Changed
- **Console banner redesign**: startup banner now displays a large ASCII art "PVP INDEX" with a Green-Yellow-Red gradient. Info line shows version, platform, and author (PVP Index).
- **Shutdown message**: updated to display "PVP INDEX has been disabled. Goodbye!" in red/grey styling.
- **Paper 26.1.x adapter build**: the `paper.26.1.x` version module now compiles against the stable Paper API `26.1.2.build.57-stable` using the Java 25 toolchain, so the adapter builds cleanly across the supported 1.21.x to 26.1.x range.
- Upgraded `maven-shade-plugin` to `3.6.2` (bundles ASM 9.8+) so the relocated bStats and Jackson dependencies shade correctly against Java 25 bytecode.

### Fixed
- **Cross-server challenges from a lobby**: challenging a player who was on a backend server (or behind another proxy) from a lobby silently timed out. Lobby servers relied on a player-location cache that went stale once a player moved to a non-lobby backend, so the challenge was routed to a node where the target was not present. The lobby now only uses the direct lobby-to-lobby Redis path when the cache confirms the target is on a live lobby node; otherwise it broadcasts the challenge over Redis to the proxies, which authoritatively resolve the target's current server and forward it. Challenges now work from any server to any server.
- **Proxy challenge routing over Redis**: Velocity proxies now handle inbound `CHALLENGE_SEND` messages over Redis (not only plugin messaging), resolving the target across all connected backends and forwarding the challenge. This removes the dependency on plugin messaging channels, which could break after a backend restarted independently of the proxy.
- **SMP challenge confirmation**: accepting an SMP-mode challenge through the risk-confirmation dialog did nothing because closing the inventory cleared the pending GUI state before it was read. The mode and challenge target are now captured before the inventory closes.
- **Return-to-origin after a cross-server battle**: players transferred to another server for a battle were left waiting on the host server for two seconds before being returned, which looked like a double teleport. They are now sent straight back to the server they came from once the battle ends.

---

## [1.0.8] - 2026-06-05

### Fixed
- **Flight exploit before battle** ([#28](https://github.com/PVP-Index/pvpindex-battles/issues/28)): players who jumped while flying before a battle were launched above the arena, causing the battle to be rejected. Flight and velocity are now neutralised before teleport, and again when applying pre-battle state.
- **Explosions destroying arena blocks** ([#29](https://github.com/PVP-Index/pvpindex-battles/issues/29)): crystal, TNT, and other explosions could break blocks in arena worlds even when `allow_block_break` was `false` in the game mode rules. Added `EntityExplodeEvent` and `BlockExplodeEvent` listeners that clear the block damage list when block breaking is disabled. Explosions still deal damage to players.

---

## [1.0.7] - 2026-06-05

### Added
- **bStats integration**: anonymous usage metrics via [bStats](https://bstats.org/plugin/bukkit/PvPIndex%20Battle/31131) (plugin ID `31131`). Shaded and relocated to `com.pvpindex.shade.bstats` to avoid conflicts with other plugins. Provides server count, player count, and version distribution data.

---

## [1.0.6] - 2026-06-05

### Added
- **Configurable after-battle location**: players can now be sent to a fixed lobby location or the world spawn after a battle ends, instead of always returning to their pre-battle position. Configured via `player_state.after_battle_location` in config.yml with three modes: `RESTORE` (default, existing behaviour), `LOBBY` (fixed coordinates), or `WORLD_SPAWN`. Closes [#16](https://github.com/PVP-Index/pvpindex-battles/issues/16).
- New `AfterBattleLocationSettings` config record in `platform-paper`.
- New config section `player_state.after_battle_location` with `mode`, `world`, `x`, `y`, `z`, `yaw`, `pitch` keys.
- `PlayerStateService.setAfterBattleLocation()` method for runtime override of the post-battle teleport destination.

### Changed
- `PlayerStateSnapshot.restore()` and `restoreWithoutInventory()` now accept an optional `Location` override parameter for the teleport destination.
- Upgraded Jackson from `2.18.2` to `2.21.3` and PlaceholderAPI from `2.11.6` to `2.12.2`.
- Removed all em dashes from comments, config files, and documentation per coding standards.
- Corrected US English spellings to UK English throughout the codebase.

---

## [1.0.5] - 2026-05-22

### Added
- **`/pvp` alias**: `/pvp` now works as an alias for `/battle`, making it easier for players to discover the command.
- **Leaderboard GUI** (`/battle leaderboard [mode]`): paginated inventory GUI showing player skulls ranked by Elo. Each skull displays wins, losses, K/D, streak, and best streak. Supports per-mode filtering (`/battle leaderboard sword`) or an overall view. Aliases: `/battle lb`, `/battle top`. Requires `database.enabled: true` in config.yml.
- New `LeaderboardGui` class in `platform-paper` with 28-entry pages, arrow-based pagination, and skull-per-player layout.
- New lang keys `leaderboard.no_data`, `leaderboard.unknown_mode`, `leaderboard.error` in all bundled language files (en, de, nl, es, pl, zh).

---

## [1.0.4] - 2026-05-22

### Added
- **Command blocking during battles**: commands are now blocked by default while a player is in an active battle. Configurable via `battle_commands` in config.yml with a whitelist of allowed commands (`/battle`, `/msg`, `/r`, `/reply`, `/tell` by default). Players with `pvpindex.battle.commands.bypass` permission can still use all commands. Closes [#19](https://github.com/PVP-Index/pvpindex-battles/issues/19).
- New `BattleCommandBlockListener` in `platform-paper` that intercepts `PlayerCommandPreprocessEvent` at `LOWEST` priority.
- New config section `battle_commands` in `config.yml` with `block_commands` toggle and `allowed_commands` whitelist.
- New permission `pvpindex.battle.commands.bypass` (default: op) to bypass command blocking.
- New lang key `battle.command_blocked` in all bundled language files (en, de, nl, es, pl, zh).

---

## [1.0.3] - 2026-05-17

### Added
- **TeamsAPI guard**: optional integration with [TeamsAPI](https://modrinth.com/plugin/teams-api) that prevents players on the same team from challenging each other. Disabled by default (`teams_guard.block_same_team: false`). Requires TeamsAPI + a compatible team plugin on the server; if either is absent, the feature is silently skipped (fail-open).
- New `TeamsGuardService` class in `platform-paper` that encapsulates the same-team lookup with graceful `NoClassDefFoundError` handling so the plugin loads cleanly whether or not TeamsAPI is on the classpath.
- `TeamsAPI` added to `softdepend` in `plugin.yml` so Paper loads it before PvPIndex Battles when both plugins are present.
- New config section `teams_guard` in `config.yml`.
- New lang keys `challenge.same_team` and `challenge.same_team_target` in all bundled language files (en, de, nl, es, pl, zh).
- JitPack repository added to parent `pom.xml`; `teams-api:1.5.0` added as a `provided` dependency in `pvpindex-platform-paper`.

### Fixed
- **Spigot/Paper/Folia compatibility**: removed all Adventure API calls that are only available on Paper. The plugin now runs on plain Spigot without `NoSuchMethodError` crashes.
  - `player.sendMessage(Component)` → `player.sendMessage(String)`
  - `player.showTitle(Title)` → `player.sendTitle(String, String, int, int, int)`
  - `player.playSound(Sound)` → `player.playSound(Location, org.bukkit.Sound, float, float)`
  - `Bukkit.createInventory(holder, size, Component)` → String overload
  - `meta.displayName(Component)` / `meta.lore(List<Component>)` → `setDisplayName` / `setLore`
  - `ClickEvent` / `HoverEvent` (Adventure) in challenge messages replaced with BungeeCord chat API (`player.spigot().sendMessage(...)`)
- MiniMessage and `&`-style colour codes are still fully supported for config authors: inputs are parsed internally via MiniMessage/LegacyAmpersand and serialised to `§`-prefixed legacy strings before being passed to Bukkit APIs.
- Enchantment glint on active GUI tabs now uses `item.addUnsafeEnchantment` (an `ItemStack` method, not `ItemMeta`), fixing a compile error against Paper API 1.21.
- Removed references to `GameRule` fields that do not exist on older Bukkit versions (`FALL_DAMAGE`, `FIRE_DAMAGE`, `FREEZE_DAMAGE`, `DROWNING_DAMAGE`).

---

## [1.0.2] - 2026-05-05

### Added
- **Lobby mode**: Paper servers can now connect directly to Redis for real-time global sync (`lobby.enabled: true`).
- **Global Player List**: real-time player visibility across all lobbies via Redis pub/sub (replaces periodic proxy dumps).
- **Global Challenges**: lobby-to-lobby challenge routing via Redis. 2-hop flow replaces the old 4-hop proxy-mediated routing.
- **Global Presence**: real-time online/offline/in-battle/in-queue status across all regions via `PRESENCE_UPDATE` messages.
- **Global Invites**: invite any player from any server with `INVITE_SEND`/`INVITE_ACCEPT`/`INVITE_DECLINE` messages.
- **Global Parties**: create, join, and manage parties across all servers with Redis-synced state. Party-aware matchmaking. Commands: `/party create`, `/party invite`, `/party join`, `/party leave`, `/party kick`, `/party disband`.
- **Global Routing**: smart region selection for cross-region battles (`ROUTE_REQUEST`/`ROUTE_RESPONSE`). Strategies: nearest, lowest latency, least loaded, shared server.
- **Optional Database Layer**: persistent storage for stats, match history, ELO ratings, and player profiles.
  - MySQL/MariaDB support with HikariCP connection pooling.
  - SQLite support for single-server or development setups.
  - MongoDB support for complex data (stub; requires driver on classpath).
  - Configurable via `database.*` section in config.yml.
- New Maven module: `pvpindex-database` with `DatabaseProvider` interface, repository abstractions, and implementations.
- `NetworkNode` class with `NodeType` enum (`PROXY`, `LOBBY`, `BACKEND`) generalising the network module.
- `LobbyNetworkService` orchestrates all Redis-based services on lobby Paper servers.
- `PlayerSyncService`, `ChallengeSyncService`, `PresenceService`, `InviteService`, `PartySyncService`, `RoutingService`, `TransferRequester` in platform-paper `network` package.
- `DataService` in platform-paper `data` package for database lifecycle management.
- `TransferRequester`: lobbies request player transfers from proxy via Redis `TRANSFER_REQUEST`.
- `TRANSFER_REQUEST` handler on Velocity and BungeeCord proxies. Executes player transfers requested by lobbies.
- New `NetworkMessageType` entries: `PRESENCE_UPDATE`, `INVITE_SEND/ACCEPT/DECLINE`, `PARTY_CREATE/JOIN/LEAVE/DISBAND/INVITE/KICK/UPDATE/CHAT`, `ROUTE_REQUEST/RESPONSE`.
- `NetworkPlayerCache` now supports incremental updates (`addPlayer`, `removePlayer`, `updateServer`) alongside bulk updates.
- `LobbySettings` and `DatabaseSettings` config records.
- New config sections: `lobby.*` (Redis connectivity for lobby servers) and `database.*` (optional persistence).
- New docs: `SETUP-LOBBY.md`, `SETUP-DATABASE.md`, `GLOBAL-FEATURES.md`, `SCALING.md`.

### Changed
- **Architecture**: lobbies now handle all global features via Redis instead of routing through proxies. Proxies are simplified to authentication, routing, and player transfers.
- Network module generalised: `ProxyNode` → `NetworkNode` with `NodeType` enum. `ProxyNode` kept as deprecated subclass.
- `NetworkRouter` interface: added `registerLocalNode()`, `getNode()`, `allNodes()`, `onlineNodes()`, `sendToNode()`. Old proxy-specific methods deprecated.
- `DefaultNetworkRouter`: refactored to use `NetworkNode` internally; backward-compatible with `ProxyNode` usage.
- `ChallengeManager`: added `setLobbyServices()` for Redis-direct challenge routing. Supports three modes: lobby (Redis), proxy (plugin messaging), standalone (local).
- Velocity `ProxyMessageHandler`: stripped all challenge routing logic. Now handles battle lifecycle, heartbeats, and `TRANSFER_REQUEST` only. Legacy challenge forwarding kept for backward compat.
- BungeeCord `BungeeProxyMessageHandler`: same simplification as Velocity.
- `PvPIndexBattlesPlugin`: added initialisation for `LobbyNetworkService` and `DataService`.
- Plugin messaging reliability: lobbies no longer depend on proxy for challenge delivery (no dropped messages when no players online).
- Challenge flow reduced from 4 hops (Paper→Proxy→Redis→Proxy→Paper) to 2 hops (Lobby→Redis→Lobby).

### Fixed
- **Challenge "Player not found" after restart**: lobby servers ignored proxy player list updates after restarting, leaving the network player cache empty for remote players. `NetworkPlayerCache` now merges incoming updates so remote players are visible within seconds of a restart.
- **Cross-proxy challenge lookup**: challenges between players on different proxies (e.g. US to EU) failed with "Player not found" because the proxy only checked its own player list. Velocity and BungeeCord proxies now fall back to the Redis player registry for cross-proxy lookups.
- **Dead player teleport failure**: after a battle, defeated players who were still dead could not be teleported, silently stranding them in deleted arena worlds. `PlayerStateSnapshot` now force-respawns dead players before attempting the teleport.
- **Cross-server return after battle**: players transferred to another server for a battle were not returned to their original server afterwards. Proxies now track each player's origin server before transfer and automatically return them when the battle ends.
- **SMP loot phase cleanup on winner disconnect**: if the winner disconnected during the SMP loot phase, the loser was left stranded with no boss bar and no timer. The plugin now ends the loot phase cleanly, restoring the loser and cancelling all timers.
- **Challenge accepted notification not delivered**: accepting or declining a challenge from a backend server through a lobby produced no response on the challenger's side. Fixed routing of `CHALLENGE_ACCEPT` and `CHALLENGE_DECLINE` messages between Redis and plugin messaging on both Velocity and BungeeCord proxies.

### Deprecated
- `ProxyNode` class. Use `NetworkNode` with `NodeType.PROXY` instead.
- `NetworkRouter.registerLocalProxy()`. Use `registerLocalNode()`.
- `NetworkRouter.getProxy()`, `allProxies()`, `onlineProxies()`. Use node-generic equivalents.
- Proxy-based challenge routing (kept as fallback for standalone/SMP servers without Redis).
- Periodic `NETWORK_PLAYER_LIST` broadcast from proxy (kept as fallback for non-lobby backends).

### Removed
- `PendingChallenge` from `platform-velocity` (challenge state no longer lives on proxy).
- `BungeePendingChallenge` from `platform-bungeecord`.
- Cross-proxy challenge routing logic from both proxy message handlers.

---

## [1.0.1] - 2026-05-04

### Added
- SMP battle item-risk confirmation GUI for challengers before sending a challenge.
- Warning message shown to challenge targets when receiving an SMP battle challenge.
- `challenge.smp_warning` language key added to all 6 locales (en, de, es, nl, pl, zh).
- Immediate "Defeated" / "Victory!" title and sound feedback for SMP battles on death.
- `allPlayers()` method on `PlayerRegistry` for cross-proxy player aggregation.

### Fixed
- Cross-proxy player visibility: network player list now merges local and remote players from all proxies.
- Proxy registration loop in `DefaultNetworkRouter`. Only publishes registration for new proxies.
- Velocity plugin initialisation order: network layer now initialises before message handlers.
- SMP loot pickup cooldown reduced from 180s to 15s (configurable in `gamemodes.yml`).

### Changed
- `BackendMessenger.broadcastNetworkPlayerList()` now accepts remote player entries from other proxies.
- `PvPIndexVelocityPlugin` scheduled task uses `broadcastCombinedPlayerList()` for merged lists.

---

## [1.0.0] - 2026-05-02

### Added
- Initial public release.
- Paper plugin supporting Minecraft 1.21.x and 1.21.4+ (Paper API 26.1.x) via a runtime version-adapter layer.
- Velocity proxy plugin for cross-server challenges and network-wide tab completion.
- 10 built-in game modes: Sword, Pot, NoDebuff, Soup, Axe, Mace, Boxing, Sumo, Crystal, UHC.
- Matchmaking queue with fully configurable 54-slot GUI (`gui.yml`), per-mode ELO, countdowns, and arena teleportation.
- Cross-server challenge system: `/battle challenge <player> [mode]` routes through Velocity or works standalone.
- Challenge UI with clickable chat accept/decline buttons.
- Replay system with frame-by-frame battle recording and `/pvpmod replay <id>` in-game playback.
- Arena pool with procedural, schematic, and world-copy generation strategies; four bundled schematics (`arena`, `colosseum`, `pvparena`, `royal`).
- Moderation suite: reports, local bans, federated network-wide bans via Velocity, real-time spectating.
- HMAC-SHA256 signed battle payloads; `require_signature` config flag to enforce verification.
- PlaceholderAPI expansion: `%pvpindex_elo%`, `%pvpindex_rank%`, `%pvpindex_in_battle%`, and more.
- Configurable messages via `messages.yml`.
- Debug logging system gated behind a config flag.
- Player state snapshots with crash recovery and cross-server transfer stabilisation.
- Custom Bukkit events for developer integration.
- GitHub Actions CI matrix (JDK 21 + 25).

---

[Unreleased]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.8...v1.1.0
[1.0.8]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/PVP-Index/pvpindex-battles/releases/tag/v1.0.0