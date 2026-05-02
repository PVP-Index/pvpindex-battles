# Paper Version Support

PvPIndex ships a single Paper JAR that supports multiple Minecraft versions through a version adapter layer.

## Supported Versions

| Minecraft Version | Paper API | Adapter Class | JDK Required | Key Differences |
|-------------------|-----------|---------------|--------------|-----------------|
| 1.21.x | 1.21.1-R0.1-SNAPSHOT | `Paper121VersionAdapter` | 21+ | `Attribute.GENERIC_MAX_HEALTH`, `Registry.EFFECT`, `Registry.ENCHANTMENT` |
| 1.21.4+ (API 26.1.x) | 26.1.2.build.52-beta | `Paper2610VersionAdapter` | 25+ | `Attribute.MAX_HEALTH`, `RegistryAccess`, `RegistryKey` |

> **Note:** Paper API 26.1.x ships class files compiled with Java 25 (class version 69). You need JDK 25+ to build the `paper.26.1.x` adapter module. The CI matrix builds with both JDK 21 and JDK 25 to verify compatibility.

## What Changed Between Versions

### Attribute Constants
- **1.21.x**: `Attribute.GENERIC_MAX_HEALTH` (enum constant)
- **26.1.x**: `Attribute.MAX_HEALTH` (interface method, `Attribute` is no longer an enum)

### Registry Access
- **1.21.x**: `Registry.EFFECT.get(NamespacedKey.minecraft("slowness"))`
- **26.1.x**: `RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(...)`

### Command Registration
- **1.21.x**: `CommandExecutor` + `TabCompleter` via `plugin.yml`
- **26.1.x**: Brigadier-based command API with `paper-plugin.yml`

The current implementation uses the 1.21.x command registration style for both versions, as Paper maintains backward compatibility for `CommandExecutor`/`TabCompleter` even on 26.1.x.

## Adding Support for New Versions

1. Create a new module under `paper-versions/` (e.g. `paper.X.Y.z/`).
2. Implement `VersionAdapter` with the correct API calls.
3. Add a `<module>` entry in the root `pom.xml`.
4. Add a detection block in `PvPIndexBattlesPlugin.resolveVersionAdapter()`.
5. Add the new module as a dependency in `bootstrap-paper/pom.xml`.
