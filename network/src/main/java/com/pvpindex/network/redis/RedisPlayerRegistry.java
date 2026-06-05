package com.pvpindex.network.redis;

import com.pvpindex.network.PlayerRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class RedisPlayerRegistry implements PlayerRegistry {

    private final ConcurrentHashMap<UUID, PlayerLocation> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byNameLower = new ConcurrentHashMap<>();

    @Override
    public void registerPlayer(UUID playerId, String playerName, String proxyId, String serverName) {
        PlayerLocation loc = new PlayerLocation(playerId, playerName, proxyId, serverName);
        byId.put(playerId, loc);
        byNameLower.put(playerName.toLowerCase(Locale.ROOT), playerId);
    }

    @Override
    public void unregisterPlayer(UUID playerId) {
        PlayerLocation old = byId.remove(playerId);
        if (old != null) {
            byNameLower.remove(old.playerName().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void switchServer(UUID playerId, String newServer) {
        byId.computeIfPresent(playerId, (id, loc) ->
                new PlayerLocation(id, loc.playerName(), loc.proxyId(), newServer));
    }

    @Override
    public Optional<PlayerLocation> findPlayer(UUID playerId) {
        return Optional.ofNullable(byId.get(playerId));
    }

    @Override
    public Optional<PlayerLocation> findPlayerByName(String name) {
        UUID id = byNameLower.get(name.toLowerCase(Locale.ROOT));
        return id != null ? findPlayer(id) : Optional.empty();
    }

    @Override
    public Set<UUID> playersOnProxy(String proxyId) {
        Set<UUID> result = new HashSet<>();
        byId.forEach((id, loc) -> {
            if (loc.proxyId().equals(proxyId)) result.add(id);
        });
        return result;
    }

    @Override
    public Set<UUID> playersOnServer(String proxyId, String serverName) {
        Set<UUID> result = new HashSet<>();
        byId.forEach((id, loc) -> {
            if (loc.proxyId().equals(proxyId) && loc.serverName().equals(serverName)) result.add(id);
        });
        return result;
    }

    @Override
    public int globalPlayerCount() {
        return byId.size();
    }

    @Override
    public int proxyPlayerCount(String proxyId) {
        return (int) byId.values().stream()
                .filter(loc -> loc.proxyId().equals(proxyId))
                .count();
    }

    @Override
    public Collection<PlayerLocation> allPlayers() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public void removeAllForProxy(String proxyId) {
        byId.entrySet().removeIf(e -> {
            if (e.getValue().proxyId().equals(proxyId)) {
                byNameLower.remove(e.getValue().playerName().toLowerCase(Locale.ROOT));
                return true;
            }
            return false;
        });
    }
}
