package com.pvpindex.battles.gamemode;

import com.pvpindex.battles.battle.type.GameModeType;
import java.util.List;

/**
 * Full configuration for a single game mode. Loaded from
 * {@code gamemodes.yml} via {@link GameModeLoader} and held in the
 * {@link GameModeRegistry}.
 *
 * <p>{@link #legacyType} keeps backwards compatibility with the original
 * {@link GameModeType} enum so existing battles / API payloads continue to
 * round-trip.</p>
 *
 * @param worldStrategy {@code copy} or {@code schematic} or {@code none}
 * @param worldTemplate id consumed by the {@code WorldGeneratorService}
 */
public record GameModeDefinition(
        String id,
        String displayName,
        List<String> description,
        GameModeType legacyType,
        GameModeRules rules,
        String kitId,
        String worldStrategy,
        String worldTemplate,
        List<String> arenas,
        List<String> winConditions
) {}
