# Changelog

All notable changes to PvPIndex Battles are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release tags use the `v` prefix (e.g. `v1.0.1`).

---

## [Unreleased]

### Added
### Changed
### Fixed
### Removed

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
- Proxy registration loop in `DefaultNetworkRouter` — only publishes registration for new proxies.
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

[Unreleased]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/PVP-Index/pvpindex-battles/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/PVP-Index/pvpindex-battles/releases/tag/v1.0.0
