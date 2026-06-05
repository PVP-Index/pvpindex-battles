package com.pvpindex.database.sql;

import com.pvpindex.database.DatabaseProvider;
import com.pvpindex.database.model.BattleRecord;
import com.pvpindex.database.model.PlayerProfile;
import com.pvpindex.database.model.PlayerStats;
import com.pvpindex.database.model.StatsDelta;
import com.pvpindex.database.repository.BattleRepository;
import com.pvpindex.database.repository.PartyRepository;
import com.pvpindex.database.repository.PlayerRepository;
import com.pvpindex.database.repository.StatsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SqlDatabaseProvider implements DatabaseProvider {

    protected DataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "pvpindex-db");
        t.setDaemon(true);
        return t;
    });

    private final PlayerRepository playerRepo = new SqlPlayerRepository();
    private final BattleRepository battleRepo = new SqlBattleRepository();
    private final StatsRepository statsRepo = new SqlStatsRepository();
    private final PartyRepository partyRepo = new PartyRepository() {
        @Override public CompletableFuture<Void> saveParty(UUID id, UUID l, Set<UUID> m) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> deleteParty(UUID id) { return CompletableFuture.completedFuture(null); }
    };

    @Override public PlayerRepository playerRepository() { return playerRepo; }
    @Override public BattleRepository battleRepository() { return battleRepo; }
    @Override public StatsRepository statsRepository() { return statsRepo; }
    @Override public PartyRepository partyRepository() { return partyRepo; }

    protected void initSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            SchemaManager.createTables(conn);
        }
    }

    private <T> CompletableFuture<T> async(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.call(); } catch (Exception e) { throw new RuntimeException(e); }
        }, executor);
    }

    private class SqlPlayerRepository implements PlayerRepository {
        @Override
        public CompletableFuture<PlayerProfile> getPlayer(UUID uuid) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement("SELECT * FROM pvpindex_players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return new PlayerProfile(uuid, rs.getString("name"),
                                Instant.ofEpochMilli(rs.getLong("first_seen")),
                                Instant.ofEpochMilli(rs.getLong("last_seen")));
                    }
                    return null;
                }
            });
        }

        @Override
        public CompletableFuture<Void> savePlayer(PlayerProfile p) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "REPLACE INTO pvpindex_players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, p.uuid().toString());
                    ps.setString(2, p.name());
                    ps.setLong(3, p.firstSeen().toEpochMilli());
                    ps.setLong(4, p.lastSeen().toEpochMilli());
                    ps.executeUpdate();
                }
                return null;
            });
        }

        @Override
        public CompletableFuture<Integer> getElo(UUID uuid, String modeId) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT elo FROM pvpindex_stats WHERE uuid = ? AND mode_id = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, modeId);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getInt("elo") : 1000;
                }
            });
        }

        @Override
        public CompletableFuture<Void> updateElo(UUID uuid, String modeId, int newElo) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "UPDATE pvpindex_stats SET elo = ? WHERE uuid = ? AND mode_id = ?")) {
                    ps.setInt(1, newElo);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, modeId);
                    ps.executeUpdate();
                }
                return null;
            });
        }
    }

    private class SqlBattleRepository implements BattleRepository {
        @Override
        public CompletableFuture<Void> saveBattle(BattleRecord r) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO pvpindex_battles (battle_id, winner_id, mode_id, duration_ms, timestamp_ms, server_name, participants) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, r.battleId().toString());
                    ps.setString(2, r.winnerId() != null ? r.winnerId().toString() : null);
                    ps.setString(3, r.modeId());
                    ps.setLong(4, r.durationMs());
                    ps.setLong(5, r.timestamp().toEpochMilli());
                    ps.setString(6, r.serverName());
                    ps.setString(7, String.join(",", r.participants().stream().map(UUID::toString).toList()));
                    ps.executeUpdate();
                }
                return null;
            });
        }

        @Override
        public CompletableFuture<List<BattleRecord>> getBattleHistory(UUID playerId, int limit, int offset) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT * FROM pvpindex_battles WHERE participants LIKE ? ORDER BY timestamp_ms DESC LIMIT ? OFFSET ?")) {
                    ps.setString(1, "%" + playerId.toString() + "%");
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                    return readBattles(ps.executeQuery());
                }
            });
        }

        @Override
        public CompletableFuture<BattleRecord> getBattle(UUID battleId) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement("SELECT * FROM pvpindex_battles WHERE battle_id = ?")) {
                    ps.setString(1, battleId.toString());
                    List<BattleRecord> list = readBattles(ps.executeQuery());
                    return list.isEmpty() ? null : list.get(0);
                }
            });
        }

        @Override
        public CompletableFuture<List<BattleRecord>> getRecentBattles(int limit) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT * FROM pvpindex_battles ORDER BY timestamp_ms DESC LIMIT ?")) {
                    ps.setInt(1, limit);
                    return readBattles(ps.executeQuery());
                }
            });
        }

        private List<BattleRecord> readBattles(ResultSet rs) throws SQLException {
            List<BattleRecord> list = new ArrayList<>();
            while (rs.next()) {
                String participantsStr = rs.getString("participants");
                List<UUID> participants = participantsStr != null && !participantsStr.isBlank()
                        ? Arrays.stream(participantsStr.split(",")).map(UUID::fromString).toList()
                        : List.of();
                String winnerStr = rs.getString("winner_id");
                list.add(new BattleRecord(
                        UUID.fromString(rs.getString("battle_id")),
                        participants,
                        winnerStr != null ? UUID.fromString(winnerStr) : null,
                        rs.getString("mode_id"),
                        rs.getLong("duration_ms"),
                        Instant.ofEpochMilli(rs.getLong("timestamp_ms")),
                        rs.getString("server_name")));
            }
            return list;
        }
    }

    private class SqlStatsRepository implements StatsRepository {
        @Override
        public CompletableFuture<PlayerStats> getStats(UUID uuid, String modeId) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT * FROM pvpindex_stats WHERE uuid = ? AND mode_id = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, modeId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return new PlayerStats(uuid, modeId, rs.getInt("wins"), rs.getInt("losses"),
                                rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("streak"),
                                rs.getInt("best_streak"), rs.getInt("elo"));
                    }
                    return new PlayerStats(uuid, modeId, 0, 0, 0, 0, 0, 0, 1000);
                }
            });
        }

        @Override
        public CompletableFuture<Void> updateStats(UUID uuid, String modeId, StatsDelta delta) {
            return async(() -> {
                try (Connection c = dataSource.getConnection()) {
                    try (PreparedStatement check = c.prepareStatement(
                            "SELECT 1 FROM pvpindex_stats WHERE uuid = ? AND mode_id = ?")) {
                        check.setString(1, uuid.toString());
                        check.setString(2, modeId);
                        if (!check.executeQuery().next()) {
                            try (PreparedStatement insert = c.prepareStatement(
                                    "INSERT INTO pvpindex_stats (uuid, mode_id) VALUES (?, ?)")) {
                                insert.setString(1, uuid.toString());
                                insert.setString(2, modeId);
                                insert.executeUpdate();
                            }
                        }
                    }

                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE pvpindex_stats SET wins = wins + ?, losses = losses + ?, " +
                                    "kills = kills + ?, deaths = deaths + ?, elo = elo + ?, " +
                                    "streak = CASE WHEN ? > 0 THEN streak + 1 ELSE 0 END, " +
                                    "best_streak = CASE WHEN ? > 0 AND streak + 1 > best_streak THEN streak + 1 ELSE best_streak END " +
                                    "WHERE uuid = ? AND mode_id = ?")) {
                        ps.setInt(1, delta.wins());
                        ps.setInt(2, delta.losses());
                        ps.setInt(3, delta.kills());
                        ps.setInt(4, delta.deaths());
                        ps.setInt(5, delta.eloDelta());
                        ps.setInt(6, delta.wins());
                        ps.setInt(7, delta.wins());
                        ps.setString(8, uuid.toString());
                        ps.setString(9, modeId);
                        ps.executeUpdate();
                    }
                }
                return null;
            });
        }

        @Override
        public CompletableFuture<List<PlayerStats>> getLeaderboard(String modeId, String stat, int limit) {
            return async(() -> {
                String orderCol = switch (stat != null ? stat.toLowerCase() : "elo") {
                    case "wins" -> "wins";
                    case "kills" -> "kills";
                    case "streak" -> "best_streak";
                    case "kdr" -> "CASE WHEN deaths = 0 THEN kills ELSE CAST(kills AS FLOAT) / deaths END";
                    default -> "elo";
                };
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT * FROM pvpindex_stats WHERE mode_id = ? ORDER BY " + orderCol + " DESC LIMIT ?")) {
                    ps.setString(1, modeId);
                    ps.setInt(2, limit);
                    ResultSet rs = ps.executeQuery();
                    List<PlayerStats> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new PlayerStats(UUID.fromString(rs.getString("uuid")), modeId,
                                rs.getInt("wins"), rs.getInt("losses"), rs.getInt("kills"),
                                rs.getInt("deaths"), rs.getInt("streak"), rs.getInt("best_streak"),
                                rs.getInt("elo")));
                    }
                    return list;
                }
            });
        }

        @Override
        public CompletableFuture<Integer> getRank(UUID uuid, String modeId) {
            return async(() -> {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT COUNT(*) + 1 AS rank FROM pvpindex_stats WHERE mode_id = ? AND elo > " +
                                     "(SELECT COALESCE(elo, 0) FROM pvpindex_stats WHERE uuid = ? AND mode_id = ?)")) {
                    ps.setString(1, modeId);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, modeId);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? rs.getInt("rank") : -1;
                }
            });
        }
    }
}
