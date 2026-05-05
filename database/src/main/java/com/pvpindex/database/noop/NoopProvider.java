package com.pvpindex.database.noop;

import com.pvpindex.database.DatabaseProvider;
import com.pvpindex.database.model.*;
import com.pvpindex.database.repository.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NoopProvider implements DatabaseProvider {

    @Override public void connect() {}
    @Override public void disconnect() {}
    @Override public boolean isConnected() { return true; }
    @Override public String type() { return "none"; }

    @Override public PlayerRepository playerRepository() { return PLAYER; }
    @Override public BattleRepository battleRepository() { return BATTLE; }
    @Override public StatsRepository statsRepository() { return STATS; }
    @Override public PartyRepository partyRepository() { return PARTY; }

    private static final PlayerRepository PLAYER = new PlayerRepository() {
        @Override public CompletableFuture<PlayerProfile> getPlayer(UUID uuid) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> savePlayer(PlayerProfile p) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Integer> getElo(UUID u, String m) { return CompletableFuture.completedFuture(1000); }
        @Override public CompletableFuture<Void> updateElo(UUID u, String m, int e) { return CompletableFuture.completedFuture(null); }
    };

    private static final BattleRepository BATTLE = new BattleRepository() {
        @Override public CompletableFuture<Void> saveBattle(BattleRecord r) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<List<BattleRecord>> getBattleHistory(UUID p, int l, int o) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<BattleRecord> getBattle(UUID id) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<List<BattleRecord>> getRecentBattles(int l) { return CompletableFuture.completedFuture(List.of()); }
    };

    private static final StatsRepository STATS = new StatsRepository() {
        @Override public CompletableFuture<PlayerStats> getStats(UUID u, String m) { return CompletableFuture.completedFuture(new PlayerStats(u, m, 0, 0, 0, 0, 0, 0, 1000)); }
        @Override public CompletableFuture<Void> updateStats(UUID u, String m, StatsDelta d) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<List<PlayerStats>> getLeaderboard(String m, String s, int l) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<Integer> getRank(UUID u, String m) { return CompletableFuture.completedFuture(-1); }
    };

    private static final PartyRepository PARTY = new PartyRepository() {
        @Override public CompletableFuture<Void> saveParty(UUID id, UUID l, Set<UUID> m) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> deleteParty(UUID id) { return CompletableFuture.completedFuture(null); }
    };
}
