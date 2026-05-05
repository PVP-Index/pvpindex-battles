package com.pvpindex.database.model;

import java.time.Instant;
import java.util.UUID;

public record PlayerProfile(UUID uuid, String name, Instant firstSeen, Instant lastSeen) {}
