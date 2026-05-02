package com.pvpindex.battles.moderation;

import java.time.Instant;
import java.util.UUID;

/**
 * One report filed by a moderator (or an in-game player) against another
 * player, optionally tied to a battle for replay context.
 */
public record ReportEntry(
        UUID id,
        UUID reportedPlayer,
        String reportedPlayerName,
        UUID reporter,
        String reporterName,
        UUID battleUuid,
        String reason,
        Instant createdAt,
        ReportStatus status
) {
    public enum ReportStatus { OPEN, REVIEWED, ACTIONED, DISMISSED }

    public ReportEntry withStatus(ReportStatus newStatus) {
        return new ReportEntry(id, reportedPlayer, reportedPlayerName, reporter, reporterName,
                battleUuid, reason, createdAt, newStatus);
    }
}
