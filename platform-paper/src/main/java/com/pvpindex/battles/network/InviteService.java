package com.pvpindex.battles.network;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends and receives invites (game, server, party) across lobbies via Redis.
 */
public final class InviteService {

    public enum InviteType { GAME_INVITE, SERVER_INVITE, PARTY_INVITE }

    public record PendingInvite(UUID inviteId, UUID senderId, String senderName,
                                UUID targetId, InviteType type, String metadata,
                                String originNodeId, Instant createdAt) {}

    private final JavaPlugin plugin;
    private final NetworkRouter router;
    private final String nodeId;
    private final ConcurrentHashMap<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();
    private static final int INVITE_TIMEOUT_SECONDS = 60;

    public InviteService(JavaPlugin plugin, NetworkRouter router, String nodeId) {
        this.plugin = plugin;
        this.router = router;
        this.nodeId = nodeId;
    }

    public void start() {
        router.addHandler(NetworkMessageType.INVITE_SEND, this::handleInviteReceive);
        router.addHandler(NetworkMessageType.INVITE_ACCEPT, this::handleInviteAccept);
        router.addHandler(NetworkMessageType.INVITE_DECLINE, this::handleInviteDecline);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireStale, 200L, 200L);
    }

    public void sendInvite(UUID senderId, String senderName, UUID targetId, InviteType type, String metadata) {
        UUID inviteId = UUID.randomUUID();
        pendingInvites.put(inviteId, new PendingInvite(
                inviteId, senderId, senderName, targetId, type, metadata, nodeId, Instant.now()));

        router.broadcast(NetworkMessageType.INVITE_SEND, Map.of(
                "inviteId", inviteId.toString(),
                "senderId", senderId.toString(),
                "senderName", senderName,
                "targetId", targetId.toString(),
                "type", type.name(),
                "metadata", metadata != null ? metadata : "",
                "originNodeId", nodeId
        ));
    }

    public void acceptInvite(UUID inviteId, UUID accepterId) {
        pendingInvites.remove(inviteId);
        router.broadcast(NetworkMessageType.INVITE_ACCEPT, Map.of(
                "inviteId", inviteId.toString(),
                "accepterId", accepterId.toString(),
                "nodeId", nodeId
        ));
    }

    public void declineInvite(UUID inviteId, UUID declinerId) {
        pendingInvites.remove(inviteId);
        router.broadcast(NetworkMessageType.INVITE_DECLINE, Map.of(
                "inviteId", inviteId.toString(),
                "declinerId", declinerId.toString(),
                "nodeId", nodeId
        ));
    }

    private void handleInviteReceive(com.pvpindex.network.NetworkMessage msg) {
        String src = msg.payloadString("originNodeId");
        if (nodeId.equals(src)) return;

        UUID inviteId = msg.payloadUuid("inviteId");
        UUID targetId = msg.payloadUuid("targetId");
        if (inviteId == null || targetId == null) return;

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) return;

        String senderName = msg.payloadString("senderName");
        String typeStr = msg.payloadString("type");
        InviteType type;
        try { type = InviteType.valueOf(typeStr); } catch (Exception e) { return; }

        pendingInvites.put(inviteId, new PendingInvite(
                inviteId, msg.payloadUuid("senderId"), senderName,
                targetId, type, msg.payloadString("metadata"), src, Instant.now()));

        if (inviteReceivedCallback != null) {
            inviteReceivedCallback.onInviteReceived(inviteId, senderName, targetId, type);
        }
    }

    private void handleInviteAccept(com.pvpindex.network.NetworkMessage msg) {
        UUID inviteId = msg.payloadUuid("inviteId");
        if (inviteId == null) return;
        PendingInvite invite = pendingInvites.remove(inviteId);
        if (invite == null) return;

        Player sender = Bukkit.getPlayer(invite.senderId());
        if (sender != null && sender.isOnline() && inviteAcceptedCallback != null) {
            inviteAcceptedCallback.onInviteAccepted(inviteId, invite.senderId());
        }
    }

    private void handleInviteDecline(com.pvpindex.network.NetworkMessage msg) {
        UUID inviteId = msg.payloadUuid("inviteId");
        if (inviteId == null) return;
        PendingInvite invite = pendingInvites.remove(inviteId);
        if (invite == null) return;

        Player sender = Bukkit.getPlayer(invite.senderId());
        if (sender != null && sender.isOnline() && inviteDeclinedCallback != null) {
            inviteDeclinedCallback.onInviteDeclined(inviteId, invite.senderId());
        }
    }

    private void expireStale() {
        Instant cutoff = Instant.now().minusSeconds(INVITE_TIMEOUT_SECONDS);
        Iterator<Map.Entry<UUID, PendingInvite>> it = pendingInvites.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().createdAt().isBefore(cutoff)) {
                it.remove();
            }
        }
    }

    private InviteReceivedCallback inviteReceivedCallback;
    private InviteAcceptedCallback inviteAcceptedCallback;
    private InviteDeclinedCallback inviteDeclinedCallback;

    public void setInviteReceivedCallback(InviteReceivedCallback cb) { this.inviteReceivedCallback = cb; }
    public void setInviteAcceptedCallback(InviteAcceptedCallback cb) { this.inviteAcceptedCallback = cb; }
    public void setInviteDeclinedCallback(InviteDeclinedCallback cb) { this.inviteDeclinedCallback = cb; }

    @FunctionalInterface public interface InviteReceivedCallback { void onInviteReceived(UUID inviteId, String senderName, UUID targetId, InviteType type); }
    @FunctionalInterface public interface InviteAcceptedCallback { void onInviteAccepted(UUID inviteId, UUID senderId); }
    @FunctionalInterface public interface InviteDeclinedCallback { void onInviteDeclined(UUID inviteId, UUID senderId); }
}
