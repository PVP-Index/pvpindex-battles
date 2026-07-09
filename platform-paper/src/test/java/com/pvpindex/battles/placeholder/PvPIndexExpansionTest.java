package com.pvpindex.battles.placeholder;

import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.identifier.WorldIdentifier;
import com.pvpindex.battles.identifier.WorldNormalizer;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class PvPIndexExpansionTest {

    private UUID playerUuid;
    private OfflinePlayer offlinePlayer;
    private PlayerStatCache statCache;
    private BattleServiceStub battleService;
    private BattleQueueServiceStub queueService;
    private VaultRewardServiceStub rewardStub;
    private PvPIndexExpansion expansion;
    private WorldNormalizer worldNormalizer;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        offlinePlayer = Mockito.mock(OfflinePlayer.class);
        Mockito.when(offlinePlayer.getUniqueId()).thenReturn(playerUuid);

        statCache = new PlayerStatCache();
        battleService = new BattleServiceStub();
        queueService = new BattleQueueServiceStub();
        rewardStub = new VaultRewardServiceStub();

        expansion = new PvPIndexExpansion(statCache, battleService, queueService);
        expansion.setVaultRewardService(rewardStub);

        worldNormalizer = new WorldNormalizer();
        worldNormalizer.register(new WorldIdentifier("mace", "Mace PvP"));
        expansion.setWorldNormalizer(worldNormalizer);
    }

    @Test
    void nullPlayerReturnsEmptyString() {
        assertEquals("", expansion.onRequest(null, "in_battle"));
    }

    @Test
    void unknownPlaceholderReturnsNull() {
        assertNull(expansion.onRequest(offlinePlayer, "not_a_placeholder"));
    }

    @Test
    void inBattleReturnsTrueWhenActive() {
        battleService.active = true;
        assertEquals("true", expansion.onRequest(offlinePlayer, "in_battle"));
    }

    @Test
    void inBattleReturnsFalseWhenInactive() {
        battleService.active = false;
        assertEquals("false", expansion.onRequest(offlinePlayer, "in_battle"));
    }

    @Test
    void queuedReturnsTrueWhenQueued() {
        queueService.queued = true;
        assertEquals("true", expansion.onRequest(offlinePlayer, "queued"));
    }

    @Test
    void queuedReturnsFalseWhenNotQueued() {
        queueService.queued = false;
        assertEquals("false", expansion.onRequest(offlinePlayer, "queued"));
    }

    @Test
    void queuedModeReturnsModeOrNone() {
        queueService.queuedMode = Optional.of("mace");
        assertEquals("mace", expansion.onRequest(offlinePlayer, "queued_mode"));

        queueService.queuedMode = Optional.empty();
        assertEquals("none", expansion.onRequest(offlinePlayer, "queued_mode"));
    }

    @Test
    void battleTypeReturnsNormalizedNameWhenAvailable() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.MACE, "srv", null);
        battleService.activeSession = Optional.of(session);

        assertEquals("Mace PvP", expansion.onRequest(offlinePlayer, "battle_type"));
        assertEquals("Mace PvP", expansion.onRequest(offlinePlayer, "battle_type_normalized"));
    }

    @Test
    void battleTypeFallsBackToRawIdWhenNotRegistered() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.SWORD, "srv", null);
        battleService.activeSession = Optional.of(session);

        assertEquals("sword", expansion.onRequest(offlinePlayer, "battle_type"));
    }

    @Test
    void battleTypeRawReturnsRawModeId() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.MACE, "srv", null);
        battleService.activeSession = Optional.of(session);

        assertEquals("mace", expansion.onRequest(offlinePlayer, "battle_type_raw"));
    }

    @Test
    void battleTypeReturnsNoneWhenNotInBattle() {
        battleService.activeSession = Optional.empty();
        assertEquals("none", expansion.onRequest(offlinePlayer, "battle_type"));
        assertEquals("none", expansion.onRequest(offlinePlayer, "battle_type_raw"));
    }

    @Test
    void battleIdReturnsModeAndFullUuid() {
        UUID battleUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BattleSession session = new BattleSession(battleUuid, BattleType.DUEL, GameModeType.MACE, "srv", null);
        battleService.activeSession = Optional.of(session);

        assertEquals("mace-550e8400-e29b-41d4-a716-446655440000",
                expansion.onRequest(offlinePlayer, "battle_id"));
    }

    @Test
    void shortBattleIdReturnsModeAndFirstSegment() {
        UUID battleUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BattleSession session = new BattleSession(battleUuid, BattleType.DUEL, GameModeType.MACE, "srv", null);
        battleService.activeSession = Optional.of(session);

        assertEquals("mace-550e8400", expansion.onRequest(offlinePlayer, "short_battle_id"));
    }

    @Test
    void battleIdReturnsNoneWhenNotInBattle() {
        battleService.activeSession = Optional.empty();
        assertEquals("none", expansion.onRequest(offlinePlayer, "battle_id"));
        assertEquals("none", expansion.onRequest(offlinePlayer, "short_battle_id"));
    }

    @Test
    void countersReflectStatCache() {
        PlayerStatCache.Entry entry = statCache.getOrCreate(playerUuid);
        entry.wins.set(7);
        entry.losses.set(3);
        entry.draws.set(1);

        assertEquals("7", expansion.onRequest(offlinePlayer, "wins"));
        assertEquals("3", expansion.onRequest(offlinePlayer, "losses"));
        assertEquals("1", expansion.onRequest(offlinePlayer, "draws"));
    }

    @Test
    void kdReturnsRatioOrWinsWhenNoLosses() {
        PlayerStatCache.Entry entry = statCache.getOrCreate(playerUuid);
        entry.wins.set(5);
        entry.losses.set(2);
        assertEquals("2.50", expansion.onRequest(offlinePlayer, "kd").replace(',', '.'));

        entry.losses.set(0);
        assertEquals("5", expansion.onRequest(offlinePlayer, "kd"));
    }

    @Test
    void eloChangeReturnsSignedValue() {
        PlayerStatCache.Entry entry = statCache.getOrCreate(playerUuid);
        entry.lastEloChange.set(18);
        assertEquals("+18", expansion.onRequest(offlinePlayer, "elo_change"));

        entry.lastEloChange.set(-12);
        assertEquals("-12", expansion.onRequest(offlinePlayer, "elo_change"));
    }

    @Test
    void eloReturnsValueForOverallAndModes() {
        PlayerStatCache.Entry entry = statCache.getOrCreate(playerUuid);
        entry.elo.put("OVERALL", 1450);
        entry.elo.put("MACE", 1320);

        assertEquals("1450", expansion.onRequest(offlinePlayer, "elo"));
        assertEquals("1320", expansion.onRequest(offlinePlayer, "elo_mace"));
        assertEquals("-", expansion.onRequest(offlinePlayer, "elo_sword"));
    }

    @Test
    void rankReturnsPrefixedValueForOverallAndModes() {
        PlayerStatCache.Entry entry = statCache.getOrCreate(playerUuid);
        entry.rank.put("OVERALL", 42);
        entry.rank.put("MACE", 5);

        assertEquals("#42", expansion.onRequest(offlinePlayer, "rank"));
        assertEquals("#5", expansion.onRequest(offlinePlayer, "rank_mace"));
        assertEquals("-", expansion.onRequest(offlinePlayer, "rank_sword"));
    }

    @Test
    void vaultRewardLastReturnsFormattedValue() {
        rewardStub.lastReward = 123.456;
        assertEquals("123.46", expansion.onRequest(offlinePlayer, "reward_last").replace(',', '.'));
    }

    @Test
    void vaultRewardLastReturnsZeroWhenServiceNull() {
        expansion.setVaultRewardService(null);
        assertEquals("0.00", expansion.onRequest(offlinePlayer, "reward_last"));
    }

    @Test
    void streakReturnsValueFromService() {
        rewardStub.streak = 4;
        assertEquals("4", expansion.onRequest(offlinePlayer, "streak"));
    }

    @Test
    void streakReturnsZeroWhenServiceNull() {
        expansion.setVaultRewardService(null);
        assertEquals("0", expansion.onRequest(offlinePlayer, "streak"));
    }

    // -------------------------------------------------------------------------
    // Lightweight stubs (avoid Mockito inline mock issues on Java 25)
    // -------------------------------------------------------------------------

    private static final class BattleServiceStub extends BattleService {
        boolean active;
        Optional<BattleSession> activeSession = Optional.empty();

        BattleServiceStub() {
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean hasActiveBattle(UUID playerUuid) {
            return active;
        }

        @Override
        public Optional<BattleSession> findActiveBattleFor(UUID playerUuid) {
            return activeSession;
        }
    }

    private static final class BattleQueueServiceStub extends com.pvpindex.battles.queue.BattleQueueService {
        boolean queued;
        Optional<String> queuedMode = Optional.empty();

        BattleQueueServiceStub() {
            super(null, null, null);
        }

        @Override
        public boolean isQueued(UUID playerUuid) {
            return queued;
        }

        @Override
        public Optional<String> getQueuedMode(UUID playerUuid) {
            return queuedMode;
        }
    }

    private static final class VaultRewardServiceStub extends com.pvpindex.battles.reward.VaultRewardService {
        double lastReward;
        int streak;

        VaultRewardServiceStub() {
            super();
        }

        @Override
        public double getLastReward(UUID playerUuid) {
            return lastReward;
        }

        @Override
        public int getStreak(UUID playerUuid) {
            return streak;
        }
    }
}
