package com.pvpindex.battles.replay;

import java.util.Map;
import java.util.UUID;

public record ReplayEvent(
        long timestampMs,
        String type,
        UUID actorUuid,
        UUID targetUuid,
        Map<String, Object> data
) {}
