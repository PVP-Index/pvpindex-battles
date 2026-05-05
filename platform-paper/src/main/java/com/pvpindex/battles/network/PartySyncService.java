package com.pvpindex.battles.network;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cross-server party state via Redis pub/sub.
 */
public final class PartySyncService {

    public record Party(UUID partyId, UUID leaderId, Set<UUID> members, int maxSize, Instant createdAt) {
        public Party withMember(UUID memberId) {
            Set<UUID> newMembers = new HashSet<>(members);
            newMembers.add(memberId);
            return new Party(partyId, leaderId, newMembers, maxSize, createdAt);
        }
        public Party withoutMember(UUID memberId) {
            Set<UUID> newMembers = new HashSet<>(members);
            newMembers.remove(memberId);
            return new Party(partyId, leaderId, newMembers, maxSize, createdAt);
        }
    }

    private final JavaPlugin plugin;
    private final NetworkRouter router;
    private final String nodeId;
    private final ConcurrentHashMap<UUID, Party> parties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> playerToParty = new ConcurrentHashMap<>();

    public PartySyncService(JavaPlugin plugin, NetworkRouter router, String nodeId) {
        this.plugin = plugin;
        this.router = router;
        this.nodeId = nodeId;
    }

    public void start() {
        router.addHandler(NetworkMessageType.PARTY_CREATE, this::handlePartyCreate);
        router.addHandler(NetworkMessageType.PARTY_JOIN, this::handlePartyJoin);
        router.addHandler(NetworkMessageType.PARTY_LEAVE, this::handlePartyLeave);
        router.addHandler(NetworkMessageType.PARTY_DISBAND, this::handlePartyDisband);
        router.addHandler(NetworkMessageType.PARTY_KICK, this::handlePartyKick);
        router.addHandler(NetworkMessageType.PARTY_UPDATE, this::handlePartyUpdate);
    }

    public Party createParty(UUID leaderId, int maxSize) {
        UUID partyId = UUID.randomUUID();
        Set<UUID> members = new HashSet<>();
        members.add(leaderId);
        Party party = new Party(partyId, leaderId, members, maxSize, Instant.now());
        parties.put(partyId, party);
        playerToParty.put(leaderId, partyId);

        router.broadcast(NetworkMessageType.PARTY_CREATE, Map.of(
                "partyId", partyId.toString(),
                "leaderId", leaderId.toString(),
                "maxSize", String.valueOf(maxSize),
                "nodeId", nodeId
        ));
        return party;
    }

    public boolean joinParty(UUID partyId, UUID playerId) {
        Party party = parties.get(partyId);
        if (party == null || party.members().size() >= party.maxSize()) return false;

        Party updated = party.withMember(playerId);
        parties.put(partyId, updated);
        playerToParty.put(playerId, partyId);

        router.broadcast(NetworkMessageType.PARTY_JOIN, Map.of(
                "partyId", partyId.toString(),
                "playerId", playerId.toString(),
                "nodeId", nodeId
        ));
        return true;
    }

    public void leaveParty(UUID playerId) {
        UUID partyId = playerToParty.remove(playerId);
        if (partyId == null) return;

        Party party = parties.get(partyId);
        if (party == null) return;

        if (party.leaderId().equals(playerId)) {
            disbandParty(partyId);
            return;
        }

        parties.put(partyId, party.withoutMember(playerId));

        router.broadcast(NetworkMessageType.PARTY_LEAVE, Map.of(
                "partyId", partyId.toString(),
                "playerId", playerId.toString(),
                "nodeId", nodeId
        ));
    }

    public void disbandParty(UUID partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;

        for (UUID member : party.members()) {
            playerToParty.remove(member);
        }

        router.broadcast(NetworkMessageType.PARTY_DISBAND, Map.of(
                "partyId", partyId.toString(),
                "nodeId", nodeId
        ));
    }

    public void kickPlayer(UUID partyId, UUID playerId) {
        Party party = parties.get(partyId);
        if (party == null) return;

        parties.put(partyId, party.withoutMember(playerId));
        playerToParty.remove(playerId);

        router.broadcast(NetworkMessageType.PARTY_KICK, Map.of(
                "partyId", partyId.toString(),
                "playerId", playerId.toString(),
                "nodeId", nodeId
        ));
    }

    public Party getParty(UUID partyId) { return parties.get(partyId); }
    public UUID getPlayerParty(UUID playerId) { return playerToParty.get(playerId); }
    public boolean isInParty(UUID playerId) { return playerToParty.containsKey(playerId); }

    private void handlePartyCreate(com.pvpindex.network.NetworkMessage msg) {
        if (nodeId.equals(msg.payloadString("nodeId"))) return;
        UUID partyId = msg.payloadUuid("partyId");
        UUID leaderId = msg.payloadUuid("leaderId");
        int maxSize = msg.payloadInt("maxSize", 5);
        if (partyId == null || leaderId == null) return;
        Set<UUID> members = new HashSet<>();
        members.add(leaderId);
        parties.put(partyId, new Party(partyId, leaderId, members, maxSize, Instant.now()));
        playerToParty.put(leaderId, partyId);
    }

    private void handlePartyJoin(com.pvpindex.network.NetworkMessage msg) {
        if (nodeId.equals(msg.payloadString("nodeId"))) return;
        UUID partyId = msg.payloadUuid("partyId");
        UUID playerId = msg.payloadUuid("playerId");
        if (partyId == null || playerId == null) return;
        Party party = parties.get(partyId);
        if (party != null) {
            parties.put(partyId, party.withMember(playerId));
            playerToParty.put(playerId, partyId);
        }
    }

    private void handlePartyLeave(com.pvpindex.network.NetworkMessage msg) {
        if (nodeId.equals(msg.payloadString("nodeId"))) return;
        UUID partyId = msg.payloadUuid("partyId");
        UUID playerId = msg.payloadUuid("playerId");
        if (partyId == null || playerId == null) return;
        Party party = parties.get(partyId);
        if (party != null) {
            parties.put(partyId, party.withoutMember(playerId));
            playerToParty.remove(playerId);
        }
    }

    private void handlePartyDisband(com.pvpindex.network.NetworkMessage msg) {
        if (nodeId.equals(msg.payloadString("nodeId"))) return;
        UUID partyId = msg.payloadUuid("partyId");
        if (partyId == null) return;
        Party party = parties.remove(partyId);
        if (party != null) {
            for (UUID member : party.members()) {
                playerToParty.remove(member);
            }
        }
    }

    private void handlePartyKick(com.pvpindex.network.NetworkMessage msg) {
        if (nodeId.equals(msg.payloadString("nodeId"))) return;
        UUID partyId = msg.payloadUuid("partyId");
        UUID playerId = msg.payloadUuid("playerId");
        if (partyId == null || playerId == null) return;
        Party party = parties.get(partyId);
        if (party != null) {
            parties.put(partyId, party.withoutMember(playerId));
            playerToParty.remove(playerId);
        }
    }

    private void handlePartyUpdate(com.pvpindex.network.NetworkMessage msg) {
        // Full state sync if needed
    }
}
