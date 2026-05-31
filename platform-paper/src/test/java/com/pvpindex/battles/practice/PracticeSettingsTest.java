package com.pvpindex.battles.practice;

import java.io.StringReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PracticeSettings}.
 *
 * <p>Exercises the {@link PracticeSettings#from(YamlConfiguration)} parser and
 * the {@link PracticeSettings#defaults()} factory.  These tests are fully
 * self-contained — no Paper server is needed because {@code YamlConfiguration}
 * is a plain SnakeYAML wrapper.</p>
 */
class PracticeSettingsTest {

    // ── helper ────────────────────────────────────────────────────────────────

    private static YamlConfiguration yaml(String content) {
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }

    // ── defaults() ────────────────────────────────────────────────────────────

    @Test
    void defaults_botUseNmsIsFalse() {
        assertFalse(PracticeSettings.defaults().botUseNms(),
                "NMS fake-player must default to disabled (experimental feature)");
    }

    @Test
    void defaults_enabledIsTrue() {
        assertTrue(PracticeSettings.defaults().enabled());
    }

    @Test
    void defaults_allNumericDefaults() {
        PracticeSettings s = PracticeSettings.defaults();
        assertEquals(5, s.reactionMaxTargets());
        assertEquals(40, s.reactionSpawnIntervalTicks());
        assertEquals(100, s.reactionTargetLifetimeTicks());
        assertEquals(6.0, s.reactionArenaSpawnRadius(), 0.001);
        assertEquals(1.5, s.reactionArenaHeightRange(), 0.001);
        assertEquals(10, s.botAttackIntervalTicks());
        assertEquals(0.28, s.botMoveSpeed(), 0.001);
        assertEquals(3.0, s.botAttackDamage(), 0.001);
    }

    // ── from(yaml) — NMS flag ─────────────────────────────────────────────────

    @Test
    void from_missingNmsKey_defaultsFalse() {
        // When the key is absent the parser should default to false.
        YamlConfiguration cfg = yaml("practice:\n  enabled: true\n");
        assertFalse(PracticeSettings.from(cfg).botUseNms());
    }

    @Test
    void from_nmsKeyExplicitFalse_isFalse() {
        YamlConfiguration cfg = yaml(
                "practice:\n  bot:\n    use_nms_fake_player: false\n");
        assertFalse(PracticeSettings.from(cfg).botUseNms());
    }

    @Test
    void from_nmsKeyExplicitTrue_isTrue() {
        YamlConfiguration cfg = yaml(
                "practice:\n  bot:\n    use_nms_fake_player: true\n");
        assertTrue(PracticeSettings.from(cfg).botUseNms(),
                "Server operator explicitly opted in to the experimental NMS bot");
    }

    // ── from(yaml) — other fields ─────────────────────────────────────────────

    @Test
    void from_enabledFalse() {
        YamlConfiguration cfg = yaml("practice:\n  enabled: false\n");
        assertFalse(PracticeSettings.from(cfg).enabled());
    }

    @Test
    void from_customBotKit() {
        YamlConfiguration cfg = yaml("practice:\n  bot:\n    kit_id: crystal_kit\n");
        assertEquals("crystal_kit", PracticeSettings.from(cfg).botKitId());
    }

    @Test
    void from_customMoveSpeed() {
        YamlConfiguration cfg = yaml("practice:\n  bot:\n    move_speed: 0.35\n");
        assertEquals(0.35, PracticeSettings.from(cfg).botMoveSpeed(), 0.001);
    }

    @Test
    void from_customReactionMaxTargets() {
        YamlConfiguration cfg =
                yaml("practice:\n  reaction_training:\n    max_targets: 8\n");
        assertEquals(8, PracticeSettings.from(cfg).reactionMaxTargets());
    }

    @Test
    void from_templateIdOverride() {
        YamlConfiguration cfg = yaml("practice:\n  template_id: custom_arena\n");
        assertEquals("custom_arena", PracticeSettings.from(cfg).practiceTemplateId());
    }
}
