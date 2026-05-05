package com.pvpindex.database.repository;

import com.pvpindex.database.model.PlayerStats;
import com.pvpindex.database.model.StatsDelta;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StatsRepository {

    CompletableFuture<PlayerStats> getStats(UUID uuid, String modeId);

    CompletableFuture<Void> updateStats(UUID uuid, String modeId, StatsDelta delta);

    CompletableFuture<List<PlayerStats>> getLeaderboard(String modeId, String stat, int limit);

    CompletableFuture<Integer> getRank(UUID uuid, String modeId);
}
