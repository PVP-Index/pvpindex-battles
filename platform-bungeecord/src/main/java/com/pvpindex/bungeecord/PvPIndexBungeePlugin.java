package com.pvpindex.bungeecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.bungeecord.command.BungeeCommandExecutor;
import com.pvpindex.bungeecord.config.BungeePluginConfig;
import com.pvpindex.bungeecord.listener.BungeePlayerConnectionListener;
import com.pvpindex.bungeecord.messaging.BungeeBackendMessenger;
import com.pvpindex.bungeecord.messaging.BungeeProxyMessageHandler;
import com.pvpindex.network.DefaultNetworkRouter;
import com.pvpindex.network.NetworkConfig;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.NodeType;
import com.pvpindex.network.redis.RedisConnectionManager;
import com.pvpindex.network.redis.RedisMessageBus;
import com.pvpindex.network.redis.RedisPlayerRegistry;
import com.pvpindex.network.redis.RedisServerRegistry;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class PvPIndexBungeePlugin extends Plugin {

    private BungeePluginConfig config;
    private ObjectMapper objectMapper;
    private BungeeBackendMessenger backendMessenger;
    private final Set<UUID> challengeTransfers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> playerOriginServers = new ConcurrentHashMap<>();

    private NetworkRouter networkRouter;
    private RedisMessageBus messageBus;
    private RedisConnectionManager redisConnectionManager;

    @Override
    public void onEnable() {
        config = BungeePluginConfig.load(getDataFolder().toPath(), getLogger());

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        backendMessenger = new BungeeBackendMessenger(this, objectMapper);

        getProxy().registerChannel(PluginChannel.PROXY);
        getProxy().getPluginManager().registerListener(this, new BungeePlayerConnectionListener(this));
        getProxy().getPluginManager().registerListener(this, new BungeeProxyMessageHandler(this, objectMapper));
        getProxy().getPluginManager().registerCommand(this, new BungeeCommandExecutor(this));

        getProxy().getScheduler().schedule(this,
                () -> backendMessenger.broadcastNetworkPlayerList(),
                10, 10, TimeUnit.SECONDS);

        initNetworkLayer();

        getLogger().info("PvPIndex BungeeCord plugin enabled. channel=" + PluginChannel.PROXY
                + " | auth=" + (!config.paperSecret().isBlank() ? "on" : "OFF")
                + " | network=" + (config.networkConfig().enabled()
                        ? "ON (" + config.networkConfig().proxyId() + ")" : "off"));
    }

    @Override
    public void onDisable() {
        shutdownNetworkLayer();
        getProxy().unregisterChannel(PluginChannel.PROXY);
        getLogger().info("PvPIndex BungeeCord plugin disabled.");
    }

    private void initNetworkLayer() {
        NetworkConfig netCfg = config.networkConfig();
        if (!netCfg.enabled()) {
            getLogger().info("[PvPIndex Network] Multi-proxy networking disabled. Single-proxy mode active.");
            return;
        }

        try {
            redisConnectionManager = new RedisConnectionManager(netCfg);
            messageBus = new RedisMessageBus(redisConnectionManager, netCfg);

            RedisPlayerRegistry netPlayerRegistry = new RedisPlayerRegistry();
            RedisServerRegistry netServerRegistry = new RedisServerRegistry();

            networkRouter = new DefaultNetworkRouter(messageBus, netPlayerRegistry, netServerRegistry, netCfg);

            messageBus.connect();
            networkRouter.start();

            NetworkNode localNode = new NetworkNode(netCfg.proxyId(), NodeType.PROXY, netCfg.region());
            localNode.heartbeat(getProxy().getOnlineCount());
            networkRouter.registerLocalNode(localNode);

            for (var entry : getProxy().getServers().entrySet()) {
                netServerRegistry.registerServer(netCfg.proxyId(),
                        entry.getKey(), entry.getValue().getSocketAddress().toString());
            }

            getLogger().info("[PvPIndex Network] Multi-proxy network layer initialised.");
        } catch (Exception e) {
            getLogger().severe("[PvPIndex Network] Failed to initialise network layer: " + e.getMessage());
            getLogger().severe("[PvPIndex Network] Cross-proxy features DISABLED. Single-proxy mode active.");
            networkRouter = null;
            messageBus = null;
            redisConnectionManager = null;
        }
    }

    private void shutdownNetworkLayer() {
        if (networkRouter != null) {
            try { networkRouter.shutdown(); } catch (Exception e) {
                getLogger().warning("[PvPIndex Network] Error during router shutdown: " + e.getMessage());
            }
        }
        if (messageBus != null) {
            try { messageBus.disconnect(); } catch (Exception e) {
                getLogger().warning("[PvPIndex Network] Error during disconnect: " + e.getMessage());
            }
        }
    }

    public BungeePluginConfig config() { return config; }
    public ObjectMapper objectMapper() { return objectMapper; }
    public BungeeBackendMessenger backendMessenger() { return backendMessenger; }
    public NetworkRouter networkRouter() { return networkRouter; }

    public boolean isNetworkEnabled() {
        return networkRouter != null && messageBus != null && messageBus.isConnected();
    }

    public void markChallengeTransfer(UUID uuid) { challengeTransfers.add(uuid); }
    public boolean isChallengeTransfer(UUID uuid) { return challengeTransfers.contains(uuid); }
    public void clearChallengeTransfer(UUID uuid) { challengeTransfers.remove(uuid); }

    public void setPlayerOriginServer(UUID uuid, String server) { playerOriginServers.put(uuid, server); }
    public String removePlayerOriginServer(UUID uuid)           { return playerOriginServers.remove(uuid); }
    public String getPlayerOriginServer(UUID uuid)              { return playerOriginServers.get(uuid); }
}
