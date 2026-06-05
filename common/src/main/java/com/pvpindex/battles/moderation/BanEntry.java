package com.pvpindex.battles.moderation;

import java.time.Instant;
import java.util.UUID;

/**
 * Single ban record. {@code scope} controls reach:
 * <ul>
 *   <li>{@code LOCAL}    . only this server (default)</li>
 *   <li>{@code FEDERATED}. published to the PvPIndex network and honoured by
 *       any other server with {@code moderation.federated_bans.enforce_inbound}
 *       enabled</li>
 * </ul>
 */
public record BanEntry(
        UUID id,
        UUID playerUuid,
        String playerName,
        UUID issuedBy,
        String issuedByName,
        String reason,
        Instant issuedAt,
        Instant expiresAt,
        Scope scope,
        UUID battleUuid,
        String sourceServerId
) {
    public enum Scope { LOCAL, FEDERATED }

    public boolean isActiveAt(Instant moment) {
        return expiresAt == null || moment.isBefore(expiresAt);
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }
}
