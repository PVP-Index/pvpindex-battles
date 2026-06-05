package com.pvpindex.network.redis;

import com.pvpindex.network.ServerRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisServerRegistry implements ServerRegistry {

    private final ConcurrentHashMap<String, Set<String>> proxyServers = new ConcurrentHashMap<>();

    @Override
    public void registerServer(String proxyId, String serverName, String address) {
        proxyServers.computeIfAbsent(proxyId, k -> ConcurrentHashMap.newKeySet()).add(serverName);
    }

    @Override
    public void unregisterServer(String proxyId, String serverName) {
        Set<String> servers = proxyServers.get(proxyId);
        if (servers != null) {
            servers.remove(serverName);
            if (servers.isEmpty()) proxyServers.remove(proxyId);
        }
    }

    @Override
    public void unregisterAllForProxy(String proxyId) {
        proxyServers.remove(proxyId);
    }

    @Override
    public Optional<String> findProxyForServer(String serverName) {
        for (var entry : proxyServers.entrySet()) {
            if (entry.getValue().contains(serverName)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<String> serversOnProxy(String proxyId) {
        Set<String> servers = proxyServers.get(proxyId);
        return servers != null ? Collections.unmodifiableSet(servers) : Collections.emptySet();
    }

    @Override
    public Map<String, Set<String>> allServers() {
        Map<String, Set<String>> snapshot = new LinkedHashMap<>();
        proxyServers.forEach((proxy, servers) -> snapshot.put(proxy, Set.copyOf(servers)));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public int globalServerCount() {
        return proxyServers.values().stream().mapToInt(Set::size).sum();
    }
}
