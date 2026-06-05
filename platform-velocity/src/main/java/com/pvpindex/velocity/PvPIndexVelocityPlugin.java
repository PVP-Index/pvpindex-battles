package com.pvpindex.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.pvpindex.battles.common.messaging.PluginChannel;
import com.pvpindex.network.DefaultNetworkRouter;
import com.pvpindex.network.NetworkConfig;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.NodeType;
import com.pvpindex.network.redis.RedisConnectionManager;
import com.pvpindex.network.redis.RedisMessageBus;
import com.pvpindex.network.redis.RedisPlayerRegistry;
import com.pvpindex.network.redis.RedisServerRegistry;
import com.pvpindex.velocity.command.VelocityPvPIndexCommand;
import com.pvpindex.velocity.config.VelocityPluginConfig;
import com.pvpindex.velocity.listener.PlayerConnectionListener;
import com.pvpindex.velocity.listener.ServerSwitchListener;
import com.pvpindex.velocity.messaging.BackendMessenger;
import com.pvpindex.velocity.messaging.ProxyMessageHandler;
import com.pvpindex.velocity.registry.BattleRegistry;
import com.pvpindex.velocity.registry.PlayerServerRegistry;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id           = "pvpindex-proxy",
        name         = "PvPIndex Proxy",
        version      = "1.0.2",
        description  = "Cross-server battle tracking and coordination for PvPIndex",
        url          = "https://pvpindex.com",
        authors      = {"PvPIndex Team"}
)
public final class PvPIndexVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityPluginConfig config;
    private ObjectMapper objectMapper;
    private PlayerServerRegistry playerRegistry;
    private BattleRegistry battleRegistry;
    private BackendMessenger backendMessenger;
    private final Set<UUID> challengeTransfers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> playerOriginServers = new ConcurrentHashMap<>();

    private NetworkRouter networkRouter;
    private RedisMessageBus messageBus;
    private RedisConnectionManager redisConnectionManager;

    @Inject
    public PvPIndexVelocityPlugin(ProxyServer server, Logger logger,
            @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = VelocityPluginConfig.load(dataDirectory, server, logger);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        playerRegistry = new PlayerServerRegistry();
        battleRegistry = new BattleRegistry();
        backendMessenger = new BackendMessenger(server, objectMapper, logger, config.paperSecret());

        MinecraftChannelIdentifier channelId = MinecraftChannelIdentifier.from(PluginChannel.PROXY);
        server.getChannelRegistrar().register(channelId);

        initNetworkLayer();

        server.getEventManager().register(this, new PlayerConnectionListener(this));
        server.getEventManager().register(this, new ServerSwitchListener(this));
        server.getEventManager().register(this, new ProxyMessageHandler(this, objectMapper));

        CommandManager cmdManager = server.getCommandManager();
        CommandMeta meta = cmdManager.metaBuilder("pvpindex")
                .aliases("pvi")
                .plugin(this)
                .build();
        cmdManager.register(meta, new VelocityPvPIndexCommand(this));

        server.getScheduler()
                .buildTask(this, this::broadcastCombinedPlayerList)
                .repeat(10, TimeUnit.SECONDS)
                .schedule();

        logger.info("PvPIndex Proxy plugin enabled. monitoring "
                + (config.monitoredServers().isEmpty()
                        ? "all servers"
                        : config.monitoredServers().size() + " server(s): " + config.monitoredServers())
                + " | channel=" + PluginChannel.PROXY
                + " | auth=" + (!config.paperSecret().isBlank() ? "on" : "OFF")
                + " | network=" + (config.networkConfig().enabled() ? "ON (" + config.networkConfig().proxyId() + ")" : "off"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdownNetworkLayer();

        server.getChannelRegistrar().unregister(
                MinecraftChannelIdentifier.from(PluginChannel.PROXY));
        logger.info("PvPIndex Proxy plugin disabled. Tracked "
                + battleRegistry.size() + " active battle(s) at shutdown.");
    }

    private void initNetworkLayer() {
        NetworkConfig netCfg = config.networkConfig();
        if (!netCfg.enabled()) {
            logger.info("[PvPIndex Network] Multi-proxy networking is disabled. Single-proxy mode active.");
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
            localNode.heartbeat(server.getPlayerCount());
            networkRouter.registerLocalNode(localNode);

            for (var rs : server.getAllServers()) {
                netServerRegistry.registerServer(netCfg.proxyId(),
                        rs.getServerInfo().getName(),
                        rs.getServerInfo().getAddress().toString());
            }

            logger.info("[PvPIndex Network] Multi-proxy network layer initialised successfully.");
        } catch (Exception e) {
            logger.severe("[PvPIndex Network] Failed to initialise network layer: " + e.getMessage());
            logger.severe("[PvPIndex Network] Cross-proxy features are DISABLED. The plugin will still work in single-proxy mode.");
            networkRouter = null;
            messageBus = null;
            redisConnectionManager = null;
        }
    }

    private void shutdownNetworkLayer() {
        if (networkRouter != null) {
            try {
                networkRouter.shutdown();
            } catch (Exception e) {
                logger.warning("[PvPIndex Network] Error during router shutdown: " + e.getMessage());
            }
        }
        if (messageBus != null) {
            try {
                messageBus.disconnect();
            } catch (Exception e) {
                logger.warning("[PvPIndex Network] Error during message bus disconnect: " + e.getMessage());
            }
        }
    }

    /**
     * Builds a combined player list from local Velocity players plus remote
     * players tracked via the cross-proxy network layer, then broadcasts
     * it to all backend Paper servers.
     */
    private void broadcastCombinedPlayerList() {
        List<Map<String, String>> remoteEntries = List.of();

        if (isNetworkEnabled()) {
            var registry = networkRouter.playerRegistry();
            String localProxyId = config.networkConfig().proxyId();
            List<Map<String, String>> remote = new ArrayList<>();
            for (var loc : registry.allPlayers()) {
                if (!loc.proxyId().equals(localProxyId)) {
                    remote.add(Map.of(
                            "name", loc.playerName(),
                            "uuid", loc.playerId().toString(),
                            "server", loc.serverName()));
                }
            }
            remoteEntries = remote;
        }

        backendMessenger.broadcastNetworkPlayerList(remoteEntries);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public ProxyServer getServer()                    { return server; }
    public Logger getLogger()                         { return logger; }
    public VelocityPluginConfig config()              { return config; }
    public PlayerServerRegistry playerRegistry()      { return playerRegistry; }
    public BattleRegistry battleRegistry()            { return battleRegistry; }
    public BackendMessenger backendMessenger()         { return backendMessenger; }

    /** Returns the network router, or {@code null} if multi-proxy is disabled. */
    public NetworkRouter networkRouter()              { return networkRouter; }

    public boolean isNetworkEnabled() {
        return networkRouter != null && messageBus != null && messageBus.isConnected();
    }

    public void markChallengeTransfer(UUID playerUuid)   { challengeTransfers.add(playerUuid); }
    public boolean isChallengeTransfer(UUID playerUuid)  { return challengeTransfers.contains(playerUuid); }
    public void clearChallengeTransfer(UUID playerUuid)  { challengeTransfers.remove(playerUuid); }

    public void setPlayerOriginServer(UUID playerUuid, String server) { playerOriginServers.put(playerUuid, server); }
    public String removePlayerOriginServer(UUID playerUuid)           { return playerOriginServers.remove(playerUuid); }
    public String getPlayerOriginServer(UUID playerUuid)              { return playerOriginServers.get(playerUuid); }

    public void reloadConfig() {
        config = VelocityPluginConfig.load(dataDirectory, server, logger);
        backendMessenger = new BackendMessenger(server, objectMapper, logger, config.paperSecret());
        logger.info("[PvPIndex] Config reloaded.");
    }
}
