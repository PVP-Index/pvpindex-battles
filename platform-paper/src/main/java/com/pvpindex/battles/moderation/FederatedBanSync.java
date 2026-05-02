package com.pvpindex.battles.moderation;

import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.config.PluginSettings;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Background poller that pulls the verified-server federated ban list from
 * the PvPIndex API and rebuilds {@link BanService}'s federated map. Only runs
 * if the local config has {@code moderation.federated_bans.enabled} on.
 */
public final class FederatedBanSync {
    private final Plugin plugin;
    private final BanService banService;
    private final PvPIndexApiClient api;
    private final ModerationSettings moderationSettings;
    private final PluginSettings pluginSettings;
    private BukkitTask task;

    public FederatedBanSync(Plugin plugin, BanService banService, PvPIndexApiClient api,
                            ModerationSettings moderationSettings, PluginSettings pluginSettings) {
        this.plugin = plugin;
        this.banService = banService;
        this.api = api;
        this.moderationSettings = moderationSettings;
        this.pluginSettings = pluginSettings;
    }

    public void start() {
        if (!moderationSettings.federatedBansEnabled() && !moderationSettings.enforceInboundFederatedBans()) {
            return;
        }
        long ticks = Math.max(20L, moderationSettings.federatedBanSyncIntervalSeconds() * 20L);
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sync, 100L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void sync() {
        try {
            List<BanEntry> remote = api.fetchFederatedBans().join();
            banService.replaceFederated(remote);
            if (pluginSettings.debug()) {
                plugin.getLogger().info("Federated ban sync: " + remote.size() + " entries");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Federated ban sync failed: " + e.getMessage());
        }
    }

    public void publish(BanEntry entry) {
        if (!moderationSettings.federatedBansEnabled()) return;
        if (entry.scope() != BanEntry.Scope.FEDERATED) return;
        api.publishFederatedBan(entry).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to publish federated ban: " + ex.getMessage());
            return false;
        });
    }
}
