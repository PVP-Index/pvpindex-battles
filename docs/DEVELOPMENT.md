# Development Guide

## Prerequisites

- Java **25+** (required to compile the Paper 26.1.x adapter; JDK 21 is sufficient for all other modules)
- Maven 3.9+
- Git

## Building

```bash
git clone https://github.com/PVP-Index/plugin.git
cd plugin
mvn clean package
```

Output JARs:
- `bootstrap-paper/target/PvPIndexBattles-1.0.0.jar`
- `bootstrap-velocity/target/PvPIndexBattles-velocity-1.0.0.jar`
- `bootstrap-bungeecord/target/PvPIndexBattles-bungeecord-1.0.0.jar`

## Running Tests

```bash
mvn test
```

Tests live in `platform-paper/src/test/java/` and `network/src/test/java/`. They use JUnit 5 and Mockito.

## Project Structure

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full module map and dependency flow.

## Adding a New Service

1. If the service is platform-agnostic (pure data, no Bukkit/Velocity/BungeeCord imports), put it in `common/`.
2. If it involves cross-proxy networking, put it in `network/`.
3. If it uses Paper API, put it in `platform-paper/`.
4. If it uses Velocity API, put it in `platform-velocity/`.
5. If it uses BungeeCord API, put it in `platform-bungeecord/`.
6. If it uses version-specific API, put the interface in `platform-paper/` and implementations in the appropriate `paper-versions/` module.
7. Wire it in the appropriate bootstrap module.

## Adding a New Version Adapter

See [VERSIONING.md](VERSIONING.md) for instructions.

## Adding a New Language

1. Copy `bootstrap-paper/src/main/resources/lang/en.yml` to a new file named `<code>.yml` (e.g. `fr.yml` for French).
2. Translate all values. Keep the `&` colour codes and `%placeholder%` tokens intact.
3. Place the file in `bootstrap-paper/src/main/resources/lang/` so it is bundled in the JAR.
4. Server owners can also drop custom language files into `plugins/PvPIndexBattles/lang/` at runtime and set `language: "fr"` in `config.yml`.
5. Run `/pvpindex reload` to apply.

## Code Style

- Java 21, use records and sealed types where appropriate.
- Tabs for indentation.
- Double quotes for strings.
- Functional style preferred; avoid mutable state where practical.
- No wildcard imports.

## CI

GitHub Actions runs on every push to `main`/`develop` and on PRs to `main`. See `.github/workflows/build.yml`.

The pipeline uses a JDK matrix:

| JDK | What it builds |
|-----|----------------|
| 21 | All modules except `paper-versions/paper.26.1.x` and `bootstrap-paper` (Paper API 26.1.x requires JDK 25) |
| 25 | Full build including 26.1.x adapter. Runs all JAR verification checks and uploads artefacts. |

Both JDK versions run tests. The JDK 25 job additionally verifies:
- Both version adapters are compiled and shaded into the Paper JAR
- All config files (`plugin.yml`, `config.yml`, `gamemodes.yml`, etc.) are bundled
- Language files (`lang/en.yml`) are bundled in the Paper JAR
- Jackson and Jedis are correctly relocated in Paper, Velocity, and BungeeCord JARs
- Velocity JAR contains the plugin class, common messaging classes, and network module
- BungeeCord JAR contains `bungee.yml` and all required classes
