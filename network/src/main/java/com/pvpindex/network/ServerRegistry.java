package com.pvpindex.network;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ServerRegistry {

    void registerServer(String proxyId, String serverName, String address);

    void unregisterServer(String proxyId, String serverName);

    void unregisterAllForProxy(String proxyId);

    Optional<String> findProxyForServer(String serverName);

    Set<String> serversOnProxy(String proxyId);

    Map<String, Set<String>> allServers();

    int globalServerCount();
}
