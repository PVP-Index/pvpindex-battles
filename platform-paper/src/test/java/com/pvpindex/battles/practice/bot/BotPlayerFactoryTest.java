package com.pvpindex.battles.practice.bot;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link BotPlayerFactory}.
 *
 * <p>Tests run on a plain JVM (no Paper server).  Bukkit interfaces are mocked with
 * Mockito.  The static {@code nmsAdapter} field is reset after each test to prevent
 * pollution between tests.</p>
 */
class BotPlayerFactoryTest {

    @AfterEach
    void resetAdapter() {
        BotPlayerFactory.setNmsAdapter(null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Plugin mockPlugin() {
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        return plugin;
    }

    /**
     * Returns a mocked {@link World} whose {@code spawnEntity} call yields a
     * mocked {@link Zombie}.  Also stubs the Zombie setters so Mockito doesn't
     * need extra setup for void-return calls.
     */
    private static World mockWorldWithZombie(Zombie zombie) {
        World world = Mockito.mock(World.class);
        Mockito.when(world.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE)))
                .thenReturn(zombie);
        return world;
    }

    // ── useNms = false ────────────────────────────────────────────────────────

    @Test
    void spawn_useNmsFalse_adapterIgnored_zombieReturned() throws Exception {
        BotNmsAdapter adapter = Mockito.mock(BotNmsAdapter.class);
        BotPlayerFactory.setNmsAdapter(adapter);

        Zombie zombie = Mockito.mock(Zombie.class);
        World world = mockWorldWithZombie(zombie);
        Location location = new Location(world, 0, 64, 0);

        LivingEntity result = BotPlayerFactory.spawn(mockPlugin(), world, location, "Bot", false);

        // Adapter must never be consulted when useNms is false.
        Mockito.verify(adapter, Mockito.never()).spawnFakePlayer(any(), any(), any());
        assertSame(zombie, result);
    }

    // ── no adapter installed ──────────────────────────────────────────────────

    @Test
    void spawn_useNmsTrue_noAdapterInstalled_zombieReturned() {
        // nmsAdapter is null (reset in @AfterEach — also ensured by @AfterEach default state)
        Zombie zombie = Mockito.mock(Zombie.class);
        World world = mockWorldWithZombie(zombie);
        Location location = new Location(world, 0, 64, 0);

        LivingEntity result = BotPlayerFactory.spawn(mockPlugin(), world, location, "Bot", true);

        assertSame(zombie, result);
    }

    // ── adapter returns null ──────────────────────────────────────────────────

    @Test
    void spawn_useNmsTrue_adapterReturnsNull_zombieReturned() throws Exception {
        BotNmsAdapter adapter = Mockito.mock(BotNmsAdapter.class);
        Mockito.when(adapter.spawnFakePlayer(any(), any(), any())).thenReturn(null);
        BotPlayerFactory.setNmsAdapter(adapter);

        Zombie zombie = Mockito.mock(Zombie.class);
        World world = mockWorldWithZombie(zombie);
        Location location = new Location(world, 0, 64, 0);

        LivingEntity result = BotPlayerFactory.spawn(mockPlugin(), world, location, "Bot", true);

        assertSame(zombie, result);
    }

    // ── adapter throws ────────────────────────────────────────────────────────

    @Test
    void spawn_useNmsTrue_adapterThrows_warningLoggedAndZombieReturned() throws Exception {
        BotNmsAdapter adapter = Mockito.mock(BotNmsAdapter.class);
        Mockito.when(adapter.spawnFakePlayer(any(), any(), any()))
                .thenThrow(new RuntimeException("NMS reflection failed"));
        BotPlayerFactory.setNmsAdapter(adapter);

        Zombie zombie = Mockito.mock(Zombie.class);
        World world = mockWorldWithZombie(zombie);
        Location location = new Location(world, 0, 64, 0);
        Plugin plugin = mockPlugin();

        LivingEntity result = BotPlayerFactory.spawn(plugin, world, location, "Bot", true);

        assertSame(zombie, result);
        // The warning is logged via plugin.getLogger().warning(...)
        Mockito.verify(plugin, Mockito.atLeastOnce()).getLogger();
    }

    // ── adapter succeeds ──────────────────────────────────────────────────────

    @Test
    void spawn_useNmsTrue_adapterSucceeds_fakePlayerReturned() throws Exception {
        LivingEntity fakePlayer = Mockito.mock(LivingEntity.class);
        BotNmsAdapter adapter = Mockito.mock(BotNmsAdapter.class);
        Mockito.when(adapter.spawnFakePlayer(any(), any(), any())).thenReturn(fakePlayer);
        BotPlayerFactory.setNmsAdapter(adapter);

        World world = Mockito.mock(World.class);
        Location location = new Location(world, 0, 64, 0);

        LivingEntity result =
                BotPlayerFactory.spawn(mockPlugin(), world, location, "TestBot", true);

        assertSame(fakePlayer, result, "Adapter's entity should be returned directly");
        Mockito.verify(adapter).spawnFakePlayer(world, location, "TestBot");
        Mockito.verify(world, Mockito.never()).spawnEntity(any(), any());
    }

    // ── adapter registration ──────────────────────────────────────────────────

    @Test
    void setNmsAdapter_null_clearsAdapterAndFallsBackToZombie() throws Exception {
        BotNmsAdapter adapter = Mockito.mock(BotNmsAdapter.class);
        Mockito.when(adapter.spawnFakePlayer(any(), any(), any()))
                .thenReturn(Mockito.mock(LivingEntity.class));
        BotPlayerFactory.setNmsAdapter(adapter);
        BotPlayerFactory.setNmsAdapter(null); // clear it

        Zombie zombie = Mockito.mock(Zombie.class);
        World world = mockWorldWithZombie(zombie);
        Location location = new Location(world, 0, 64, 0);

        LivingEntity result = BotPlayerFactory.spawn(mockPlugin(), world, location, "Bot", true);

        Mockito.verify(adapter, Mockito.never()).spawnFakePlayer(any(), any(), any());
        assertSame(zombie, result);
    }
}
