package com.pvpindex.battles.gamemode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store for {@link GameModeDefinition} and {@link KitDefinition}
 * loaded from disk. Lookup is case-insensitive.
 */
public final class GameModeRegistry {
    private final Map<String, GameModeDefinition> modes = new LinkedHashMap<>();
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();

    public void replaceAll(Collection<GameModeDefinition> newModes, Collection<KitDefinition> newKits) {
        modes.clear();
        kits.clear();
        newModes.forEach(m -> modes.put(m.id().toLowerCase(), m));
        newKits.forEach(k -> kits.put(k.id().toLowerCase(), k));
    }

    public Optional<GameModeDefinition> findMode(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(modes.get(id.toLowerCase()));
    }

    public Optional<KitDefinition> findKit(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(kits.get(id.toLowerCase()));
    }

    public Collection<GameModeDefinition> allModes() { return modes.values(); }
    public Collection<KitDefinition> allKits() { return kits.values(); }
}
