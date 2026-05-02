package com.pvpindex.battles.gamemode;

import java.util.List;

/**
 * Loadout that participants receive when a battle starts. Kits can be shared
 * across multiple game modes by id (referenced from
 * {@link GameModeDefinition#kitId()}).
 */
public record KitDefinition(
        String id,
        String displayName,
        List<KitItem> items,
        List<String> potionEffects
) {}
