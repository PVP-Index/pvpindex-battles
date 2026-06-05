package com.pvpindex.battles.messaging;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the list of online players across the entire network.
 *
 * <p>Supports two update modes:</p>
 * <ul>
 *   <li><b>Bulk update</b>. periodic {@code NETWORK_PLAYER_LIST} from proxy
 *       (legacy, used by SMP/backend servers without Redis)</li>
 *   <li><b>Incremental update</b>. real-time {@code PLAYER_JOIN/LEAVE/SWITCH}
 *       events via Redis (used by lobby servers with Redis)</li>
 * </ul>
 *
 * <p>Thread-safe: reads and writes may happen from any thread.</p>
 */
public final class NetworkPlayerCache {

    private final ConcurrentHashMap<UUID, NetworkPlayer> playerMap = new ConcurrentHashMap<>();
    private volatile long lastUpdateEpochMs = 0;
    private volatile boolean lobbyMode = false;

    /**
     * Enables lobby mode. When active, proxy bulk updates are ignored
     * because Redis {@link com.pvpindex.battles.network.PlayerSyncService}
     * is the authoritative source of player data.
     */
    public void setLobbyMode(boolean lobbyMode) {
        this.lobbyMode = lobbyMode;
    }

    /**
     * Replace the entire cached player list. Called when a
     * {@code NETWORK_PLAYER_LIST} message arrives from the proxy.
     *
     * <p>In lobby mode, proxy data is merged with {@code putIfAbsent}
     * rather than replacing the cache. This seeds the cache with remote
     * players after a server restart while Redis incremental updates
     * remain authoritative for any player already tracked.</p>
     */
    public void update(List<NetworkPlayer> newPlayers) {
        if (lobbyMode) {
            for (NetworkPlayer p : newPlayers) {
                playerMap.putIfAbsent(p.uuid(), p);
            }
            this.lastUpdateEpochMs = System.currentTimeMillis();
            return;
        }
        playerMap.clear();
        for (NetworkPlayer p : newPlayers) {
            playerMap.put(p.uuid(), p);
        }
        this.lastUpdateEpochMs = System.currentTimeMillis();
    }

    /** Add or update a single player (real-time Redis events). */
    public void addPlayer(String name, UUID uuid, String server) {
        playerMap.put(uuid, new NetworkPlayer(name, uuid, server));
        this.lastUpdateEpochMs = System.currentTimeMillis();
    }

    /** Remove a single player (real-time Redis events). */
    public void removePlayer(UUID uuid) {
        playerMap.remove(uuid);
        this.lastUpdateEpochMs = System.currentTimeMillis();
    }

    /** Update the server for a player (real-time Redis events). */
    public void updateServer(UUID uuid, String newServer) {
        playerMap.computeIfPresent(uuid, (id, old) -> new NetworkPlayer(old.name(), id, newServer));
        this.lastUpdateEpochMs = System.currentTimeMillis();
    }

    /** All player names currently known across the network. */
    public List<String> allNames() {
        return playerMap.values().stream().map(NetworkPlayer::name).toList();
    }

    /** Full player records. */
    public List<NetworkPlayer> allPlayers() {
        return List.copyOf(playerMap.values());
    }

    /** Millisecond epoch of the last successful update, or 0 if never updated. */
    public long lastUpdateEpochMs() {
        return lastUpdateEpochMs;
    }

    /** Whether the cache has received at least one update from the proxy. */
    public boolean isPopulated() {
        return lastUpdateEpochMs > 0;
    }

    /** Find a player by name (case-insensitive). */
    public NetworkPlayer findByName(String name) {
        for (NetworkPlayer p : playerMap.values()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** Find a player by UUID. */
    public NetworkPlayer findByUuid(UUID uuid) {
        return playerMap.get(uuid);
    }

    public record NetworkPlayer(String name, UUID uuid, String server) {}
}
