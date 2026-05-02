package com.pvpindex.battles.replay;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable container for a recorded battle replay. Holds both the
 * high-level {@link ReplayEvent} log (used for analytics / dispute review)
 * and the dense {@link ReplayFrame} stream (used for visual rewatch).
 */
public record ReplayFile(
        int version,
        UUID battleUuid,
        String detailLevel,
        int tickRate,
        Instant startedAt,
        Instant endedAt,
        Map<String, Object> arena,
        List<ReplayEvent> events,
        List<ReplayFrame> frames
) {
    public static final int CURRENT_VERSION = 2;
}
