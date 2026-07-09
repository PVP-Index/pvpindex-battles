package com.pvpindex.battles.placeholder;

import com.pvpindex.battles.api.PvPIndexApiClient;
import com.pvpindex.battles.battle.BattleService;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.battle.type.BattleType;
import com.pvpindex.battles.battle.type.GameModeType;
import com.pvpindex.battles.identifier.WorldIdentifier;
import com.pvpindex.battles.identifier.WorldNormalizer;
import com.pvpindex.battles.queue.BattleQueueService;
import com.pvpindex.battles.reward.VaultRewardService;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class PvPIndexExpansionTest {

    private UUID playerUuid;
    private OfflinePlayer offlinePlayer;
    private PlayerStatCache statCache;
    private BattleService battleService;
    private BattleQueueService queueService;
    private VaultRewardService vaultRewardService;
    private PvPIndexExpansion expansion;
    private WorldNormalizer worldNormalizer;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        offlinePlayer = Mockito.mock(OfflinePlayer.class);
        Mockito.when(offlinePlayer.getUniqueId()).thenReturn(playerUuid);

        PvPIndexApiClient apiClient = Mockito.mock(PvPIndexApiClient.class);
        statCache = new PlayerStatCache(apiClient, Logger.getLogger("test"));

        battleService = Mockito.mock(BattleService.class);
        queueService = Mockito.mock(BattleQueueService.class);
        vaultRewardService = Mockito.mock(VaultRewardService.class);

        expansion = new PvPIndexExpansion(statCache, battleService, queueService);
        expansion.setVaultRewardService(vaultRewardService);

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
        Mockito.when(battleService.hasActiveBattle(playerUuid)).thenReturn(true);
        assertEquals("true", expansion.onRequest(offlinePlayer, "in_battle"));
    }

    @Test
    void inBattleReturnsFalseWhenInactive() {
        Mockito.when(battleService.hasActiveBattle(playerUuid)).thenReturn(false);
        assertEquals("false", expansion.onRequest(offlinePlayer, "in_battle"));
    }

    @Test
    void queuedReturnsTrueWhenQueued() {
        Mockito.when(queueService.isQueued(playerUuid)).thenReturn(true);
        assertEquals("true", expansion.onRequest(offlinePlayer, "queued"));
    }

    @Test
    void queuedReturnsFalseWhenNotQueued() {
        Mockito.when(queueService.isQueued(playerUuid)).thenReturn(false);
        assertEquals("false", expansion.onRequest(offlinePlayer, "queued"));
    }

    @Test
    void queuedModeReturnsModeOrNone() {
        Mockito.when(queueService.getQueuedMode(playerUuid)).thenReturn(Optional.of("mace"));
        assertEquals("mace", expansion.onRequest(offlinePlayer, "queued_mode"));

        Mockito.when(queueService.getQueuedMode(playerUuid)).thenReturn(Optional.empty());
        assertEquals("none", expansion.onRequest(offlinePlayer, "queued_mode"));
    }

    @Test
    void battleTypeReturnsNormalizedNameWhenAvailable() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.MACE, "srv", null);
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.of(session));

        assertEquals("Mace PvP", expansion.onRequest(offlinePlayer, "battle_type"));
        assertEquals("Mace PvP", expansion.onRequest(offlinePlayer, "battle_type_normalized"));
    }

    @Test
    void battleTypeFallsBackToRawIdWhenNotRegistered() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.SWORD, "srv", null);
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.of(session));

        assertEquals("sword", expansion.onRequest(offlinePlayer, "battle_type"));
    }

    @Test
    void battleTypeRawReturnsRawModeId() {
        BattleSession session = new BattleSession(UUID.randomUUID(), BattleType.DUEL, GameModeType.MACE, "srv", null);
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.of(session));

        assertEquals("mace", expansion.onRequest(offlinePlayer, "battle_type_raw"));
    }

    @Test
    void battleTypeReturnsNoneWhenNotInBattle() {
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.empty());
        assertEquals("none", expansion.onRequest(offlinePlayer, "battle_type"));
        assertEquals("none", expansion.onRequest(offlinePlayer, "battle_type_raw"));
    }

    @Test
    void battleIdReturnsModeAndFullUuid() {
        UUID battleUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BattleSession session = new BattleSession(battleUuid, BattleType.DUEL, GameModeType.MACE, "srv", null);
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.of(session));

        assertEquals("mace-550e8400-e29b-41d4-a716-446655440000",
                expansion.onRequest(offlinePlayer, "battle_id"));
    }

    @Test
    void shortBattleIdReturnsModeAndFirstSegment() {
        UUID battleUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        BattleSession session = new BattleSession(battleUuid, BattleType.DUEL, GameModeType.MACE, "srv", null);
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.of(session));

        assertEquals("mace-550e8400", expansion.onRequest(offlinePlayer, "short_battle_id"));
    }

    @Test
    void battleIdReturnsNoneWhenNotInBattle() {
        Mockito.when(battleService.findActiveBattleFor(playerUuid)).thenReturn(Optional.empty());
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
        Mockito.when(vaultRewardService.getLastReward(playerUuid)).thenReturn(123.456);
        assertEquals("123.46", expansion.onRequest(offlinePlayer, "reward_last").replace(',', '.'));
    }

    @Test
    void vaultRewardLastReturnsZeroWhenServiceNull() {
        expansion.setVaultRewardService(null);
        assertEquals("0.00", expansion.onRequest(offlinePlayer, "reward_last"));
    }

    @Test
    void streakReturnsValueFromService() {
        Mockito.when(vaultRewardService.getStreak(playerUuid)).thenReturn(4);
        assertEquals("4", expansion.onRequest(offlinePlayer, "streak"));
    }

    @Test
    void streakReturnsZeroWhenServiceNull() {
        expansion.setVaultRewardService(null);
        assertEquals("0", expansion.onRequest(offlinePlayer, "streak"));
    }
}
