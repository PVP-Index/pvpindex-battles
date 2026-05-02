package com.pvpindex.velocity.registry;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping player UUIDs to their current backend
 * {@link RegisteredServer}.
 *
 * <p>Updated by:</p>
 * <ul>
 *   <li>{@code PostLoginEvent} — player connects to the proxy</li>
 *   <li>{@code ServerConnectedEvent} — player moves to a different backend</li>
 *   <li>{@code DisconnectEvent} — player leaves the network</li>
 * </ul>
 */
public final class PlayerServerRegistry {

    private final ConcurrentHashMap<UUID, RegisteredServer> playerToServer = new ConcurrentHashMap<>();

    /** Called when a player successfully connects to a backend. */
    public void put(UUID playerUuid, RegisteredServer server) {
        playerToServer.put(playerUuid, server);
    }

    /** Called when a player disconnects from the proxy entirely. */
    public void remove(UUID playerUuid) {
        playerToServer.remove(playerUuid);
    }

    /** Returns the backend the player is currently on, or empty if not tracked. */
    public Optional<RegisteredServer> getServer(UUID playerUuid) {
        return Optional.ofNullable(playerToServer.get(playerUuid));
    }

    /** Returns the server name the player is currently on, or empty. */
    public Optional<String> getServerName(UUID playerUuid) {
        return getServer(playerUuid).map(s -> s.getServerInfo().getName());
    }

    /** Returns all currently tracked player UUIDs. */
    public Collection<UUID> trackedPlayers() {
        return Collections.unmodifiableSet(playerToServer.keySet());
    }

    /** Total number of players currently tracked by this registry. */
    public int size() {
        return playerToServer.size();
    }
}
