package com.pvpindex.battles;

import com.pvpindex.battles.battle.PlayerStateService;
import com.pvpindex.battles.config.AfterBattleLocationSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerStateServiceBackPreventionTest {

    @TempDir
    Path tempDir;

    private Plugin plugin;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setup() {
        plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), eq(1L))).thenReturn(mock(BukkitTask.class));
    }

    @Test
    void afterBattleLocationSettingsModeMatchesConfiguredValue() {
        AfterBattleLocationSettings settings = new AfterBattleLocationSettings(
                AfterBattleLocationSettings.Mode.RESTORE,
                "world", 0.0, 64.0, 0.0, 0f, 0f);
        assertEquals(AfterBattleLocationSettings.Mode.RESTORE, settings.mode());
    }

    @Test
    void afterBattleLocationSettingsLobbyModeConfigured() {
        AfterBattleLocationSettings settings = new AfterBattleLocationSettings(
                AfterBattleLocationSettings.Mode.LOBBY,
                "spawn", 100.0, 64.0, 200.0, 90f, 0f);
        assertEquals(AfterBattleLocationSettings.Mode.LOBBY, settings.mode());
        assertEquals("spawn", settings.world());
        assertEquals(100.0, settings.x());
    }

    @Test
    void preventBackCommandEnabled_serviceAcceptsFlag() {
        com.pvpindex.battles.version.VersionAdapter adapter = mock(com.pvpindex.battles.version.VersionAdapter.class);
        when(adapter.getMaxHealthAttribute()).thenReturn(Attribute.GENERIC_MAX_HEALTH);

        PlayerStateService service = new PlayerStateService(plugin, false, adapter, true);
        assertNotNull(service);
    }

    @Test
    void preventBackCommandDisabled_serviceAcceptsFlag() {
        com.pvpindex.battles.version.VersionAdapter adapter = mock(com.pvpindex.battles.version.VersionAdapter.class);
        when(adapter.getMaxHealthAttribute()).thenReturn(Attribute.GENERIC_MAX_HEALTH);

        PlayerStateService service = new PlayerStateService(plugin, false, adapter, false);
        assertNotNull(service);
    }

    @Test
    void setAfterBattleLocation_storesLocation() {
        com.pvpindex.battles.version.VersionAdapter adapter = mock(com.pvpindex.battles.version.VersionAdapter.class);
        when(adapter.getMaxHealthAttribute()).thenReturn(Attribute.GENERIC_MAX_HEALTH);

        PlayerStateService service = new PlayerStateService(plugin, false, adapter, true);

        World world = mock(World.class);
        Location location = new Location(world, 100, 64, 200);
        service.setAfterBattleLocation(location);

        assertNotNull(service);
    }
}
