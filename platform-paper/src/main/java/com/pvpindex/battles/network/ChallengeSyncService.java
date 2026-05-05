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
 * Handles challenge send/accept/deny directly via Redis between lobbies.
 * Replaces the old proxy-mediated challenge routing.
 */
public final class ChallengeSyncService {

    private final JavaPlugin plugin;
    private final NetworkRouter router;
    private final String nodeId;

    private final ConcurrentHashMap<UUID, PendingRedisChallenge> pending = new ConcurrentHashMap<>();

    public record PendingRedisChallenge(
            UUID challengeId, UUID challengerUuid, String challengerName,
            UUID targetUuid, String targetName, String targetNodeId,
            String modeId, Instant createdAt, boolean accepted
    ) {}

    public ChallengeSyncService(JavaPlugin plugin, NetworkRouter router, String nodeId) {
        this.plugin = plugin;
        this.router = router;
        this.nodeId = nodeId;
    }

    public void start() {
        router.addHandler(NetworkMessageType.CHALLENGE_SEND, this::handleChallengeReceive);
        router.addHandler(NetworkMessageType.CHALLENGE_ACCEPT, this::handleChallengeAccepted);
        router.addHandler(NetworkMessageType.CHALLENGE_DENY, this::handleChallengeDenied);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireStale, 100L, 100L);
    }

    public void sendChallenge(UUID challengeId, UUID challengerUuid, String challengerName,
                              String targetName, UUID targetUuid, String targetNodeId, String modeId) {
        pending.put(challengeId, new PendingRedisChallenge(
                challengeId, challengerUuid, challengerName,
                targetUuid, targetName, targetNodeId, modeId, Instant.now(), false));

        router.sendToNode(targetNodeId, NetworkMessageType.CHALLENGE_SEND, Map.of(
                "challengeId", challengeId.toString(),
                "challengerUuid", challengerUuid.toString(),
                "challengerName", challengerName,
                "targetUuid", targetUuid.toString(),
                "targetName", targetName,
                "modeId", modeId != null ? modeId : "",
                "originNodeId", nodeId
        ));
    }

    public void acceptChallenge(UUID challengeId, UUID accepterUuid) {
        PendingRedisChallenge prc = pending.get(challengeId);
        if (prc != null) {
            // prc.targetNodeId() holds the origin node that sent the challenge
            String originNode = prc.targetNodeId();
            if (originNode != null && !originNode.equals(nodeId)) {
                router.sendToNode(originNode, NetworkMessageType.CHALLENGE_ACCEPT, Map.of(
                        "challengeId", challengeId.toString(),
                        "accepterUuid", accepterUuid.toString()
                ));
            } else {
                router.broadcast(NetworkMessageType.CHALLENGE_ACCEPT, Map.of(
                        "challengeId", challengeId.toString(),
                        "accepterUuid", accepterUuid.toString(),
                        "nodeId", nodeId
                ));
            }
            return;
        }
        router.broadcast(NetworkMessageType.CHALLENGE_ACCEPT, Map.of(
                "challengeId", challengeId.toString(),
                "accepterUuid", accepterUuid.toString(),
                "nodeId", nodeId
        ));
    }

    public void declineChallenge(UUID challengeId, UUID declinerUuid) {
        pending.remove(challengeId);
        router.broadcast(NetworkMessageType.CHALLENGE_DENY, Map.of(
                "challengeId", challengeId.toString(),
                "declinerUuid", declinerUuid.toString(),
                "nodeId", nodeId
        ));
    }

    private void handleChallengeReceive(com.pvpindex.network.NetworkMessage msg) {
        UUID challengeId = msg.payloadUuid("challengeId");
        UUID challengerUuid = msg.payloadUuid("challengerUuid");
        String challengerName = msg.payloadString("challengerName");
        UUID targetUuid = msg.payloadUuid("targetUuid");
        String modeId = msg.payloadString("modeId");
        String originNodeId = msg.payloadString("originNodeId");
        if (challengeId == null || targetUuid == null) return;

        pending.put(challengeId, new PendingRedisChallenge(
                challengeId, challengerUuid, challengerName,
                targetUuid, null, originNodeId, modeId, Instant.now(), false));

        // Delegate to ChallengeManager to show the challenge UI
        // This will be wired in by PvPIndexBattlesPlugin
        if (challengeReceivedCallback != null) {
            challengeReceivedCallback.onChallengeReceived(challengeId, challengerName, challengerUuid, modeId, targetUuid);
        }
    }

    private void handleChallengeAccepted(com.pvpindex.network.NetworkMessage msg) {
        UUID challengeId = msg.payloadUuid("challengeId");
        if (challengeId == null) return;

        PendingRedisChallenge prc = pending.remove(challengeId);
        if (prc == null) return;

        if (challengeAcceptedCallback != null) {
            UUID accepterUuid = msg.payloadUuid("accepterUuid");
            challengeAcceptedCallback.onChallengeAccepted(challengeId, prc.challengerUuid(),
                    accepterUuid != null ? accepterUuid : prc.targetUuid(), prc.modeId());
        }
    }

    private void handleChallengeDenied(com.pvpindex.network.NetworkMessage msg) {
        UUID challengeId = msg.payloadUuid("challengeId");
        if (challengeId == null) return;

        PendingRedisChallenge prc = pending.remove(challengeId);
        if (prc == null) return;

        Player challenger = Bukkit.getPlayer(prc.challengerUuid());
        if (challenger != null && challenger.isOnline() && challengeDeniedCallback != null) {
            challengeDeniedCallback.onChallengeDenied(challengeId, prc.challengerUuid());
        }
    }

    private void expireStale() {
        Instant cutoff = Instant.now().minusSeconds(60);
        Iterator<Map.Entry<UUID, PendingRedisChallenge>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            PendingRedisChallenge prc = it.next().getValue();
            if (prc.createdAt().isBefore(cutoff)) {
                it.remove();
                Player challenger = Bukkit.getPlayer(prc.challengerUuid());
                if (challenger != null && challenger.isOnline() && challengeDeniedCallback != null) {
                    challengeDeniedCallback.onChallengeDenied(prc.challengeId(), prc.challengerUuid());
                }
            }
        }
    }

    // Callbacks wired by PvPIndexBattlesPlugin
    private ChallengeReceivedCallback challengeReceivedCallback;
    private ChallengeAcceptedCallback challengeAcceptedCallback;
    private ChallengeDeniedCallback challengeDeniedCallback;

    public void setChallengeReceivedCallback(ChallengeReceivedCallback cb) { this.challengeReceivedCallback = cb; }
    public void setChallengeAcceptedCallback(ChallengeAcceptedCallback cb) { this.challengeAcceptedCallback = cb; }
    public void setChallengeDeniedCallback(ChallengeDeniedCallback cb) { this.challengeDeniedCallback = cb; }

    @FunctionalInterface
    public interface ChallengeReceivedCallback {
        void onChallengeReceived(UUID challengeId, String challengerName, UUID challengerUuid, String modeId, UUID targetUuid);
    }

    @FunctionalInterface
    public interface ChallengeAcceptedCallback {
        void onChallengeAccepted(UUID challengeId, UUID challengerUuid, UUID targetUuid, String modeId);
    }

    @FunctionalInterface
    public interface ChallengeDeniedCallback {
        void onChallengeDenied(UUID challengeId, UUID challengerUuid);
    }

    public ConcurrentHashMap<UUID, PendingRedisChallenge> pendingChallenges() { return pending; }
}
