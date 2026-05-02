package com.pvpindex.battles.common.messaging;

/**
 * Plugin messaging channel identifiers used between the Paper backend plugin
 * and the Velocity proxy plugin.
 *
 * <p>Both ends register the same channel identifier. Messages are serialised
 * to JSON bytes via {@link BattleMessage}.</p>
 *
 * <p>The channel follows the {@code namespace:path} format required by modern
 * Minecraft protocol versions. Messages sent on {@link #PROXY} travel in both
 * directions (Paper ↔ Velocity).</p>
 */
public final class PluginChannel {

    /** Primary bidirectional channel for all PvPIndex proxy messages. */
    public static final String PROXY = "pvpindex:proxy";

    private PluginChannel() {}
}
