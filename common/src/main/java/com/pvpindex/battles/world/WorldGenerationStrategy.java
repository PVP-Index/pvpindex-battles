package com.pvpindex.battles.world;

/**
 * Pluggable arena generation backend. Implementations:
 * <ul>
 *   <li>{@link WorldCopyStrategy} — duplicates an entire template world
 *   directory and loads it as a brand-new world per battle. Heaviest but
 *   simplest, perfect isolation.</li>
 *   <li>{@link SchematicStrategy} — pastes a {@code .schem} into a configured
 *   host arena world at an offset, then clears blocks afterwards. Lighter,
 *   useful for many concurrent battles in one shared world.</li>
 * </ul>
 */
public interface WorldGenerationStrategy {
    String id();

    ArenaInstance generate(ArenaTemplate template) throws Exception;

    void release(ArenaInstance instance) throws Exception;
}
