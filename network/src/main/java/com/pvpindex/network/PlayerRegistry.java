package com.pvpindex.network;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlayerRegistry {

    void registerPlayer(UUID playerId, String playerName, String proxyId, String serverName);

    void unregisterPlayer(UUID playerId);

    void switchServer(UUID playerId, String newServer);

    Optional<PlayerLocation> findPlayer(UUID playerId);

    Optional<PlayerLocation> findPlayerByName(String name);

    Set<UUID> playersOnProxy(String proxyId);

    Set<UUID> playersOnServer(String proxyId, String serverName);

    int globalPlayerCount();

    int proxyPlayerCount(String proxyId);

    record PlayerLocation(UUID playerId, String playerName, String proxyId, String serverName) {}
}
