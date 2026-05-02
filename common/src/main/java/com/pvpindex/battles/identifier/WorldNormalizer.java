package com.pvpindex.battles.identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry mapping raw game-mode IDs to their display-friendly
 * {@link WorldIdentifier}. Populated at startup from {@code GameModeDefinition}
 * entries and queried by GUI, placeholder, command, and Velocity messaging code.
 *
 * <p>This class is <b>not</b> used by any API-submission path.</p>
 */
public final class WorldNormalizer {

	private final Map<String, WorldIdentifier> byRaw = new LinkedHashMap<>();
	private final Map<String, WorldIdentifier> byNormalized = new LinkedHashMap<>();

	public void register(WorldIdentifier id) {
		byRaw.put(id.rawId().toLowerCase(), id);
		byNormalized.put(id.normalizedId().toLowerCase(), id);
	}

	public Optional<WorldIdentifier> getByRaw(String rawId) {
		return rawId == null ? Optional.empty() : Optional.ofNullable(byRaw.get(rawId.toLowerCase()));
	}

	public Optional<WorldIdentifier> getByNormalized(String normalizedId) {
		return normalizedId == null ? Optional.empty() : Optional.ofNullable(byNormalized.get(normalizedId.toLowerCase()));
	}

	/**
	 * Attempts lookup by raw first, then by normalised name.
	 * Useful for command input where the player might type either form.
	 */
	public Optional<WorldIdentifier> resolve(String input) {
		Optional<WorldIdentifier> byRawResult = getByRaw(input);
		return byRawResult.isPresent() ? byRawResult : getByNormalized(input);
	}

	public Collection<WorldIdentifier> all() {
		return byRaw.values();
	}

	public void clear() {
		byRaw.clear();
		byNormalized.clear();
	}
}
