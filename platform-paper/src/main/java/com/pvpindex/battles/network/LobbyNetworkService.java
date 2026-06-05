package com.pvpindex.battles.network;

import com.pvpindex.battles.config.LobbySettings;
import com.pvpindex.network.DefaultNetworkRouter;
import com.pvpindex.network.NetworkConfig;
import com.pvpindex.network.NetworkRouter;
import com.pvpindex.network.node.NetworkNode;
import com.pvpindex.network.node.NodeType;
import com.pvpindex.network.redis.RedisConnectionManager;
import com.pvpindex.network.redis.RedisMessageBus;
import com.pvpindex.network.redis.RedisPlayerRegistry;
import com.pvpindex.network.redis.RedisServerRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Central hub for all Redis-based networking on lobby Paper servers.
 * Only initialised when {@code lobby.enabled = true} in config.yml.
 */
public final class LobbyNetworkService {

    private final JavaPlugin plugin;
    private final LobbySettings settings;
    private final Logger logger;

    private RedisConnectionManager connectionManager;
    private RedisMessageBus messageBus;
    private NetworkRouter router;
    private NetworkNode localNode;

    private PlayerSyncService playerSyncService;
    private ChallengeSyncService challengeSyncService;
    private PresenceService presenceService;
    private InviteService inviteService;
    private PartySyncService partySyncService;
    private RoutingService routingService;
    private TransferRequester transferRequester;

    public LobbyNetworkService(JavaPlugin plugin, LobbySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.logger = plugin.getLogger();
    }

    public void start() {
        NetworkConfig netCfg = NetworkConfig.builder()
                .proxyId(settings.nodeId())
                .region(settings.region())
                .enabled(true)
                .redisHost(settings.redisHost())
                .redisPort(settings.redisPort())
                .redisPassword(settings.redisPassword())
                .redisDatabase(settings.redisDatabase())
                .redisPoolSize(settings.redisPoolSize())
                .build();

        try {
            connectionManager = new RedisConnectionManager(netCfg);
            messageBus = new RedisMessageBus(connectionManager, netCfg);

            RedisPlayerRegistry playerRegistry = new RedisPlayerRegistry();
            RedisServerRegistry serverRegistry = new RedisServerRegistry();

            router = new DefaultNetworkRouter(messageBus, playerRegistry, serverRegistry, netCfg);

            messageBus.connect();
            router.start();

            localNode = new NetworkNode(settings.nodeId(), NodeType.LOBBY, settings.region());
            localNode.heartbeat(plugin.getServer().getOnlinePlayers().size());
            router.registerLocalNode(localNode);

            playerSyncService = new PlayerSyncService(plugin, router, settings.nodeId());
            challengeSyncService = new ChallengeSyncService(plugin, router, settings.nodeId());
            presenceService = new PresenceService(plugin, router, settings.nodeId());
            inviteService = new InviteService(plugin, router, settings.nodeId());
            partySyncService = new PartySyncService(plugin, router, settings.nodeId());
            routingService = new RoutingService(router, settings.nodeId());
            transferRequester = new TransferRequester(router, settings.nodeId());

            playerSyncService.start();
            challengeSyncService.start();
            presenceService.start();
            inviteService.start();
            partySyncService.start();

            logger.info("[PvPIndex Lobby] Network service started - node=" + settings.nodeId()
                    + " region=" + settings.region()
                    + " redis=" + settings.redisHost() + ":" + settings.redisPort());
        } catch (Exception e) {
            logger.severe("[PvPIndex Lobby] Failed to start network service: " + e.getMessage());
            logger.severe("[PvPIndex Lobby] Lobby features DISABLED. Falling back to standalone mode.");
            shutdown();
        }
    }

    public void shutdown() {
        if (router != null) {
            try { router.shutdown(); } catch (Exception e) {
                logger.warning("[PvPIndex Lobby] Error during router shutdown: " + e.getMessage());
            }
        }
        if (messageBus != null) {
            try { messageBus.disconnect(); } catch (Exception e) {
                logger.warning("[PvPIndex Lobby] Error during disconnect: " + e.getMessage());
            }
        }
        router = null;
        messageBus = null;
        connectionManager = null;
    }

    public boolean isActive() {
        return router != null && messageBus != null && messageBus.isConnected();
    }

    public NetworkRouter router() { return router; }
    public PlayerSyncService playerSync() { return playerSyncService; }
    public ChallengeSyncService challengeSync() { return challengeSyncService; }
    public PresenceService presence() { return presenceService; }
    public InviteService invites() { return inviteService; }
    public PartySyncService parties() { return partySyncService; }
    public RoutingService routing() { return routingService; }
    public TransferRequester transfers() { return transferRequester; }
}
