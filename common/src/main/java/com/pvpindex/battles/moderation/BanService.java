package com.pvpindex.battles.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local ban store. Bans default to {@link BanEntry.Scope#LOCAL} and never
 * leave this server unless an admin explicitly issues them as
 * {@link BanEntry.Scope#FEDERATED}, in which case
 * {@link FederatedBanSync} publishes them to the PvPIndex network.
 *
 * <p>Inbound federated bans are stored in a separate map and are only
 * enforced when {@link com.pvpindex.battles.moderation.ModerationSettings#enforceInboundFederatedBans()}
 * is true — the user's "optional" cross-server ban toggle.</p>
 */
public final class BanService {
    private final ObjectMapper mapper;
    private final Path localFile;
    private final Path federatedFile;
    private final Map<UUID, BanEntry> localBans = new ConcurrentHashMap<>();
    private final Map<UUID, BanEntry> federatedBans = new ConcurrentHashMap<>();

    public BanService(ObjectMapper mapper, Path dataDir) {
        this.mapper = mapper;
        this.localFile = dataDir.resolve("bans-local.json");
        this.federatedFile = dataDir.resolve("bans-federated.json");
    }

    public synchronized void load() throws IOException {
        localBans.clear();
        federatedBans.clear();
        if (Files.exists(localFile)) {
            for (BanEntry e : mapper.readValue(localFile.toFile(), new TypeReference<ArrayList<BanEntry>>() {})) {
                localBans.put(e.playerUuid(), e);
            }
        }
        if (Files.exists(federatedFile)) {
            for (BanEntry e : mapper.readValue(federatedFile.toFile(), new TypeReference<ArrayList<BanEntry>>() {})) {
                federatedBans.put(e.playerUuid(), e);
            }
        }
    }

    public synchronized void persist() throws IOException {
        Files.createDirectories(localFile.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(localFile.toFile(), List.copyOf(localBans.values()));
        mapper.writerWithDefaultPrettyPrinter().writeValue(federatedFile.toFile(), List.copyOf(federatedBans.values()));
    }

    public BanEntry ban(BanEntry entry) throws IOException {
        if (entry.scope() == BanEntry.Scope.FEDERATED) {
            // Federated bans are also enforced locally on the issuing server.
            localBans.put(entry.playerUuid(), entry);
        } else {
            localBans.put(entry.playerUuid(), entry);
        }
        persist();
        return entry;
    }

    public synchronized void replaceFederated(List<BanEntry> entries) throws IOException {
        federatedBans.clear();
        for (BanEntry entry : entries) {
            federatedBans.put(entry.playerUuid(), entry);
        }
        persist();
    }

    public synchronized boolean unban(UUID playerUuid) throws IOException {
        boolean removed = localBans.remove(playerUuid) != null;
        if (removed) persist();
        return removed;
    }

    /**
     * Resolve the active ban for a player, considering whether inbound
     * federated bans should be honoured. Returns empty if the player is
     * allowed to join.
     */
    public Optional<BanEntry> activeBan(UUID playerUuid, boolean enforceFederated) {
        Instant now = Instant.now();
        BanEntry local = localBans.get(playerUuid);
        if (local != null && local.isActiveAt(now)) return Optional.of(local);
        if (enforceFederated) {
            BanEntry fed = federatedBans.get(playerUuid);
            if (fed != null && fed.isActiveAt(now)) return Optional.of(fed);
        }
        return Optional.empty();
    }

    public List<BanEntry> localBans() { return List.copyOf(localBans.values()); }
    public List<BanEntry> federatedBans() { return List.copyOf(federatedBans.values()); }
}
