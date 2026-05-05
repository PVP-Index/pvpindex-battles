package com.pvpindex.battles.network;

import com.pvpindex.network.NetworkMessageType;
import com.pvpindex.network.NetworkRouter;

import java.util.Map;
import java.util.UUID;

/**
 * Requests player transfers from proxy via Redis.
 * The proxy listens for TRANSFER_REQUEST and executes the transfer.
 */
public final class TransferRequester {

    private final NetworkRouter router;
    private final String nodeId;

    public TransferRequester(NetworkRouter router, String nodeId) {
        this.router = router;
        this.nodeId = nodeId;
    }

    public void requestTransfer(UUID playerId, String targetServer) {
        router.broadcast(NetworkMessageType.TRANSFER_REQUEST, Map.of(
                "playerId", playerId.toString(),
                "targetServer", targetServer,
                "requestingNode", nodeId
        ));
    }

    public void requestTransfer(UUID playerId, String targetServer, String reason) {
        router.broadcast(NetworkMessageType.TRANSFER_REQUEST, Map.of(
                "playerId", playerId.toString(),
                "targetServer", targetServer,
                "requestingNode", nodeId,
                "reason", reason != null ? reason : ""
        ));
    }
}
