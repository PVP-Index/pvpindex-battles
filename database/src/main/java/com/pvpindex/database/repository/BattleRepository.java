package com.pvpindex.database.repository;

import com.pvpindex.database.model.BattleRecord;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BattleRepository {

    CompletableFuture<Void> saveBattle(BattleRecord record);

    CompletableFuture<List<BattleRecord>> getBattleHistory(UUID playerId, int limit, int offset);

    CompletableFuture<BattleRecord> getBattle(UUID battleId);

    CompletableFuture<List<BattleRecord>> getRecentBattles(int limit);
}
