package com.pvpindex.battles.gui;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Holds every configurable value for the battle and challenge GUIs.
 * Loaded from {@code gui.yml} in the plugin data folder.
 */
public final class GuiConfig {

	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

	// ── Battle GUI ──────────────────────────────────────────────────────────
	private Component battleTitle;
	private int battleRows;

	private Material titleMaterial;
	private Component titleQueueName;
	private Component titleChallengeName;
	private int titleSlot;

	private int modeSlotStart;
	private int modeSlotEnd;
	private String modeNamePrefix;
	private String queueCountLine;
	private String clickLine;

	private Material queueTabMaterial;
	private Component queueTabActive;
	private Component queueTabInactive;
	private int queueTabSlot;

	private Material challengeTabMaterial;
	private Component challengeTabActive;
	private Component challengeTabInactive;
	private int challengeTabSlot;

	private Material activeBattlesMaterial;
	private Component activeBattlesName;
	private String activeBattlesLoreFormat;
	private int activeBattlesSlot;

	private Material leaveMaterial;
	private Component leaveName;
	private List<Component> leaveLore;
	private int leaveSlot;

	private Material closeMaterial;
	private Component closeName;
	private int closeSlot;

	private Map<String, Material> modeIcons;
	private Material defaultIcon;

	// ── Challenge GUI ───────────────────────────────────────────────────────
	private Component challengeTitle;
	private int challengeRows;

	private Material acceptMaterial;
	private String acceptNameFormat;
	private List<String> acceptLoreFormat;
	private int acceptSlot;

	private Material infoMaterial;
	private String infoNameFormat;
	private List<String> infoLoreFormat;
	private int infoSlot;

	private Material declineMaterial;
	private String declineNameFormat;
	private List<String> declineLoreFormat;
	private int declineSlot;

	private int challengeTimeoutSeconds;

	// ── Confirmation GUI (SMP risk warning) ─────────────────────────────────
	private Component confirmationTitle;
	private int confirmationRows;

	private Material confirmInfoMaterial;
	private Component confirmInfoName;
	private List<Component> confirmInfoLore;
	private int confirmInfoSlot;

	private Material confirmYesMaterial;
	private Component confirmYesName;
	private List<Component> confirmYesLore;
	private int confirmYesSlot;

	private Material confirmCancelMaterial;
	private Component confirmCancelName;
	private List<Component> confirmCancelLore;
	private int confirmCancelSlot;

	private GuiConfig() {}

	// ── Factory ─────────────────────────────────────────────────────────────

	public static GuiConfig load(File dataFolder, Logger logger) {
		File file = new File(dataFolder, "gui.yml");
		if (!file.exists()) {
			logger.warning("gui.yml not found — using built-in defaults.");
			return defaults();
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		GuiConfig cfg = new GuiConfig();
		cfg.loadBattleGui(yaml.getConfigurationSection("battle_gui"), logger);
		cfg.loadChallengeGui(yaml.getConfigurationSection("challenge_gui"), logger);
		cfg.loadConfirmationGui(yaml.getConfigurationSection("confirmation_gui"), logger);
		return cfg;
	}

	public static GuiConfig defaults() {
		GuiConfig cfg = new GuiConfig();
		cfg.battleTitle = Component.text()
				.append(Component.text("\u2694 ", NamedTextColor.DARK_GRAY))
				.append(Component.text("Battle Queue", NamedTextColor.WHITE))
				.build();
		cfg.battleRows = 6;
		cfg.titleMaterial = Material.NETHER_STAR;
		cfg.titleQueueName = Component.text("Battle Queue", NamedTextColor.GOLD, TextDecoration.BOLD);
		cfg.titleChallengeName = Component.text("Challenge Mode", NamedTextColor.GOLD, TextDecoration.BOLD);
		cfg.titleSlot = 4;
		cfg.modeSlotStart = 9;
		cfg.modeSlotEnd = 35;
		cfg.modeNamePrefix = "&e&l";
		cfg.queueCountLine = "&7In queue: %count%";
		cfg.clickLine = "&aClick to select";

		cfg.queueTabMaterial = Material.GOLD_BLOCK;
		cfg.queueTabActive = Component.text("Queue", NamedTextColor.GREEN, TextDecoration.BOLD);
		cfg.queueTabInactive = Component.text("Queue", NamedTextColor.GRAY, TextDecoration.BOLD);
		cfg.queueTabSlot = 36;
		cfg.challengeTabMaterial = Material.DIAMOND_SWORD;
		cfg.challengeTabActive = Component.text("Challenge", NamedTextColor.GREEN, TextDecoration.BOLD);
		cfg.challengeTabInactive = Component.text("Challenge", NamedTextColor.GRAY, TextDecoration.BOLD);
		cfg.challengeTabSlot = 37;
		cfg.activeBattlesMaterial = Material.RED_WOOL;
		cfg.activeBattlesName = Component.text("Active Battles", NamedTextColor.RED);
		cfg.activeBattlesLoreFormat = "&7%count% battle(s) running";
		cfg.activeBattlesSlot = 38;

		cfg.leaveMaterial = Material.BARRIER;
		cfg.leaveName = Component.text("Leave Queue", NamedTextColor.RED);
		cfg.leaveLore = List.of(Component.text("Click to leave your current queue.", NamedTextColor.GRAY));
		cfg.leaveSlot = 49;
		cfg.closeMaterial = Material.BARRIER;
		cfg.closeName = Component.text("Close", NamedTextColor.DARK_GRAY);
		cfg.closeSlot = 53;

		cfg.modeIcons = defaultModeIcons();
		cfg.defaultIcon = Material.COMPASS;

		cfg.challengeTitle = Component.text("Challenge", NamedTextColor.GOLD);
		cfg.challengeRows = 1;
		cfg.acceptMaterial = Material.LIME_WOOL;
		cfg.acceptNameFormat = "&a&lAccept";
		cfg.acceptLoreFormat = List.of(
				"&7Challenger: %challenger%", "&7Mode: %mode%", "", "&aClick to accept");
		cfg.acceptSlot = 2;
		cfg.infoMaterial = Material.PAPER;
		cfg.infoNameFormat = "&6&lChallenge";
		cfg.infoLoreFormat = List.of(
				"&7From: %challenger%", "&7Mode: %mode%", "&8Expires in %timeout%s");
		cfg.infoSlot = 4;
		cfg.declineMaterial = Material.RED_WOOL;
		cfg.declineNameFormat = "&c&lDecline";
		cfg.declineLoreFormat = List.of("", "&cClick to decline");
		cfg.declineSlot = 6;
		cfg.challengeTimeoutSeconds = 30;

		cfg.confirmationTitle = Component.text("Risk Warning", NamedTextColor.RED, TextDecoration.BOLD);
		cfg.confirmationRows = 3;
		cfg.confirmInfoMaterial = Material.BARRIER;
		cfg.confirmInfoName = Component.text("Warning: Item Risk", NamedTextColor.RED, TextDecoration.BOLD);
		cfg.confirmInfoLore = List.of(
				Component.text("You are about to enter an SMP battle.", NamedTextColor.GRAY),
				Component.text("If you lose, the winner can take your items.", NamedTextColor.GRAY),
				Component.empty(),
				Component.text("Are you sure you want to proceed?", NamedTextColor.YELLOW));
		cfg.confirmInfoSlot = 4;
		cfg.confirmYesMaterial = Material.LIME_WOOL;
		cfg.confirmYesName = Component.text("Yes, I am sure", NamedTextColor.GREEN, TextDecoration.BOLD);
		cfg.confirmYesLore = List.of(Component.text("Click to join the queue.", NamedTextColor.GRAY));
		cfg.confirmYesSlot = 11;
		cfg.confirmCancelMaterial = Material.RED_WOOL;
		cfg.confirmCancelName = Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD);
		cfg.confirmCancelLore = List.of(Component.text("Click to go back.", NamedTextColor.GRAY));
		cfg.confirmCancelSlot = 15;

		return cfg;
	}

	// ── Section loaders ─────────────────────────────────────────────────────

	private void loadBattleGui(ConfigurationSection sec, Logger logger) {
		if (sec == null) {
			GuiConfig d = defaults();
			copyBattleFrom(d);
			return;
		}

		battleTitle = parse(sec.getString("title", "&8\u2694 &fBattle Queue"));
		battleRows = clampRows(sec.getInt("rows", 6));

		ConfigurationSection ti = sec.getConfigurationSection("title_item");
		titleMaterial = mat(ti, "material", Material.NETHER_STAR, logger);
		titleQueueName = parse(strOr(ti, "queue_name", "&6&lBattle Queue"));
		titleChallengeName = parse(strOr(ti, "challenge_name", "&6&lChallenge Mode"));
		titleSlot = intOr(ti, "slot", 4);

		ConfigurationSection ms = sec.getConfigurationSection("mode_slots");
		modeSlotStart = intOr(ms, "start", 9);
		modeSlotEnd = intOr(ms, "end", 35);

		ConfigurationSection mi = sec.getConfigurationSection("mode_item");
		modeNamePrefix = strOr(mi, "name_prefix", "&e&l");
		queueCountLine = strOr(mi, "queue_count_line", "&7In queue: %count%");
		clickLine = strOr(mi, "click_line", "&aClick to select");

		ConfigurationSection cats = sec.getConfigurationSection("categories");
		loadCategoryQueue(cats != null ? cats.getConfigurationSection("queue") : null, logger);
		loadCategoryChallenge(cats != null ? cats.getConfigurationSection("challenge") : null, logger);
		loadCategoryActiveBattles(cats != null ? cats.getConfigurationSection("active_battles") : null, logger);

		ConfigurationSection lb = sec.getConfigurationSection("leave_button");
		leaveMaterial = mat(lb, "material", Material.BARRIER, logger);
		leaveName = parse(strOr(lb, "name", "&cLeave Queue"));
		leaveLore = lb != null
				? lb.getStringList("lore").stream().map(GuiConfig::parse).collect(Collectors.toList())
				: List.of(Component.text("Click to leave your current queue.", NamedTextColor.GRAY));
		leaveSlot = intOr(lb, "slot", 49);

		ConfigurationSection cb = sec.getConfigurationSection("close_button");
		closeMaterial = mat(cb, "material", Material.BARRIER, logger);
		closeName = parse(strOr(cb, "name", "&8Close"));
		closeSlot = intOr(cb, "slot", 53);

		ConfigurationSection icons = sec.getConfigurationSection("mode_icons");
		modeIcons = new LinkedHashMap<>();
		if (icons != null) {
			for (String key : icons.getKeys(false)) {
				Material m = safeMat(icons.getString(key), logger);
				if (m != null) modeIcons.put(key.toLowerCase(), m);
			}
		} else {
			modeIcons = defaultModeIcons();
		}
		defaultIcon = safeMat(sec.getString("default_icon", "COMPASS"), logger);
		if (defaultIcon == null) defaultIcon = Material.COMPASS;
	}

	private void loadCategoryQueue(ConfigurationSection sec, Logger logger) {
		queueTabMaterial = mat(sec, "material", Material.GOLD_BLOCK, logger);
		queueTabActive = parse(strOr(sec, "name", "&a&lQueue"));
		queueTabInactive = parse(strOr(sec, "inactive_name", "&7&lQueue"));
		queueTabSlot = intOr(sec, "slot", 36);
	}

	private void loadCategoryChallenge(ConfigurationSection sec, Logger logger) {
		challengeTabMaterial = mat(sec, "material", Material.DIAMOND_SWORD, logger);
		challengeTabActive = parse(strOr(sec, "name", "&a&lChallenge"));
		challengeTabInactive = parse(strOr(sec, "inactive_name", "&7&lChallenge"));
		challengeTabSlot = intOr(sec, "slot", 37);
	}

	private void loadCategoryActiveBattles(ConfigurationSection sec, Logger logger) {
		activeBattlesMaterial = mat(sec, "material", Material.RED_WOOL, logger);
		activeBattlesName = parse(strOr(sec, "name", "&cActive Battles"));
		activeBattlesLoreFormat = strOr(sec, "lore", "&7%count% battle(s) running");
		activeBattlesSlot = intOr(sec, "slot", 38);
	}

	private void loadChallengeGui(ConfigurationSection sec, Logger logger) {
		if (sec == null) {
			GuiConfig d = defaults();
			copyChallengeFrom(d);
			return;
		}

		challengeTitle = parse(sec.getString("title", "&6Challenge"));
		challengeRows = clampRows(sec.getInt("rows", 1));

		ConfigurationSection ai = sec.getConfigurationSection("accept_item");
		acceptMaterial = mat(ai, "material", Material.LIME_WOOL, logger);
		acceptNameFormat = strOr(ai, "name", "&a&lAccept");
		acceptLoreFormat = ai != null ? ai.getStringList("lore") : List.of(
				"&7Challenger: %challenger%", "&7Mode: %mode%", "", "&aClick to accept");
		acceptSlot = intOr(ai, "slot", 2);

		ConfigurationSection ii = sec.getConfigurationSection("info_item");
		infoMaterial = mat(ii, "material", Material.PAPER, logger);
		infoNameFormat = strOr(ii, "name", "&6&lChallenge");
		infoLoreFormat = ii != null ? ii.getStringList("lore") : List.of(
				"&7From: %challenger%", "&7Mode: %mode%", "&8Expires in %timeout%s");
		infoSlot = intOr(ii, "slot", 4);

		ConfigurationSection di = sec.getConfigurationSection("decline_item");
		declineMaterial = mat(di, "material", Material.RED_WOOL, logger);
		declineNameFormat = strOr(di, "name", "&c&lDecline");
		declineLoreFormat = di != null ? di.getStringList("lore") : List.of("", "&cClick to decline");
		declineSlot = intOr(di, "slot", 6);

		challengeTimeoutSeconds = sec.getInt("timeout_seconds", 30);
	}

	private void loadConfirmationGui(ConfigurationSection sec, Logger logger) {
		if (sec == null) {
			GuiConfig d = defaults();
			copyConfirmationFrom(d);
			return;
		}

		confirmationTitle = parse(sec.getString("title", "&c&lRisk Warning"));
		confirmationRows = clampRows(sec.getInt("rows", 3));

		ConfigurationSection ii = sec.getConfigurationSection("info_item");
		confirmInfoMaterial = mat(ii, "material", Material.BARRIER, logger);
		confirmInfoName = parse(strOr(ii, "name", "&c&lWarning: Item Risk"));
		confirmInfoLore = ii != null
				? ii.getStringList("lore").stream().map(GuiConfig::parse).collect(Collectors.toList())
				: defaults().confirmInfoLore;
		confirmInfoSlot = intOr(ii, "slot", 4);

		ConfigurationSection ci = sec.getConfigurationSection("confirm_item");
		confirmYesMaterial = mat(ci, "material", Material.LIME_WOOL, logger);
		confirmYesName = parse(strOr(ci, "name", "&a&lYes, I am sure"));
		confirmYesLore = ci != null
				? ci.getStringList("lore").stream().map(GuiConfig::parse).collect(Collectors.toList())
				: defaults().confirmYesLore;
		confirmYesSlot = intOr(ci, "slot", 11);

		ConfigurationSection ca = sec.getConfigurationSection("cancel_item");
		confirmCancelMaterial = mat(ca, "material", Material.RED_WOOL, logger);
		confirmCancelName = parse(strOr(ca, "name", "&c&lCancel"));
		confirmCancelLore = ca != null
				? ca.getStringList("lore").stream().map(GuiConfig::parse).collect(Collectors.toList())
				: defaults().confirmCancelLore;
		confirmCancelSlot = intOr(ca, "slot", 15);
	}

	// ── Copy helpers (for partial fallback) ─────────────────────────────────

	private void copyBattleFrom(GuiConfig d) {
		battleTitle = d.battleTitle; battleRows = d.battleRows;
		titleMaterial = d.titleMaterial; titleQueueName = d.titleQueueName;
		titleChallengeName = d.titleChallengeName; titleSlot = d.titleSlot;
		modeSlotStart = d.modeSlotStart; modeSlotEnd = d.modeSlotEnd;
		modeNamePrefix = d.modeNamePrefix; queueCountLine = d.queueCountLine;
		clickLine = d.clickLine;
		queueTabMaterial = d.queueTabMaterial; queueTabActive = d.queueTabActive;
		queueTabInactive = d.queueTabInactive; queueTabSlot = d.queueTabSlot;
		challengeTabMaterial = d.challengeTabMaterial; challengeTabActive = d.challengeTabActive;
		challengeTabInactive = d.challengeTabInactive; challengeTabSlot = d.challengeTabSlot;
		activeBattlesMaterial = d.activeBattlesMaterial; activeBattlesName = d.activeBattlesName;
		activeBattlesLoreFormat = d.activeBattlesLoreFormat; activeBattlesSlot = d.activeBattlesSlot;
		leaveMaterial = d.leaveMaterial; leaveName = d.leaveName;
		leaveLore = d.leaveLore; leaveSlot = d.leaveSlot;
		closeMaterial = d.closeMaterial; closeName = d.closeName; closeSlot = d.closeSlot;
		modeIcons = d.modeIcons; defaultIcon = d.defaultIcon;
	}

	private void copyChallengeFrom(GuiConfig d) {
		challengeTitle = d.challengeTitle; challengeRows = d.challengeRows;
		acceptMaterial = d.acceptMaterial; acceptNameFormat = d.acceptNameFormat;
		acceptLoreFormat = d.acceptLoreFormat; acceptSlot = d.acceptSlot;
		infoMaterial = d.infoMaterial; infoNameFormat = d.infoNameFormat;
		infoLoreFormat = d.infoLoreFormat; infoSlot = d.infoSlot;
		declineMaterial = d.declineMaterial; declineNameFormat = d.declineNameFormat;
		declineLoreFormat = d.declineLoreFormat; declineSlot = d.declineSlot;
		challengeTimeoutSeconds = d.challengeTimeoutSeconds;
	}

	private void copyConfirmationFrom(GuiConfig d) {
		confirmationTitle = d.confirmationTitle; confirmationRows = d.confirmationRows;
		confirmInfoMaterial = d.confirmInfoMaterial; confirmInfoName = d.confirmInfoName;
		confirmInfoLore = d.confirmInfoLore; confirmInfoSlot = d.confirmInfoSlot;
		confirmYesMaterial = d.confirmYesMaterial; confirmYesName = d.confirmYesName;
		confirmYesLore = d.confirmYesLore; confirmYesSlot = d.confirmYesSlot;
		confirmCancelMaterial = d.confirmCancelMaterial; confirmCancelName = d.confirmCancelName;
		confirmCancelLore = d.confirmCancelLore; confirmCancelSlot = d.confirmCancelSlot;
	}

	// ── Utility ─────────────────────────────────────────────────────────────

	private static Component parse(String raw) {
		if (raw == null || raw.isEmpty()) return Component.empty();
		return LEGACY.deserialize(raw);
	}

	private static Material mat(ConfigurationSection sec, String key, Material fallback, Logger logger) {
		if (sec == null) return fallback;
		String raw = sec.getString(key);
		if (raw == null) return fallback;
		Material m = safeMat(raw, logger);
		return m != null ? m : fallback;
	}

	private static Material safeMat(String raw, Logger logger) {
		if (raw == null) return null;
		try {
			return Material.valueOf(raw.toUpperCase().trim());
		} catch (IllegalArgumentException e) {
			logger.warning("[gui.yml] Unknown material '" + raw + "' — skipping.");
			return null;
		}
	}

	private static String strOr(ConfigurationSection sec, String key, String fallback) {
		return sec != null ? sec.getString(key, fallback) : fallback;
	}

	private static int intOr(ConfigurationSection sec, String key, int fallback) {
		return sec != null ? sec.getInt(key, fallback) : fallback;
	}

	private static int clampRows(int rows) {
		return Math.max(1, Math.min(6, rows));
	}

	private static Map<String, Material> defaultModeIcons() {
		Map<String, Material> icons = new LinkedHashMap<>();
		icons.put("crystal", Material.END_CRYSTAL);
		icons.put("crystal_pvp", Material.END_CRYSTAL);
		icons.put("uhc", Material.GOLDEN_APPLE);
		icons.put("sword", Material.IRON_SWORD);
		icons.put("sword_pvp", Material.IRON_SWORD);
		icons.put("axe", Material.NETHERITE_AXE);
		icons.put("axe_pvp", Material.NETHERITE_AXE);
		icons.put("mace", Material.MACE);
		icons.put("mace_pvp", Material.MACE);
		icons.put("pot", Material.SPLASH_POTION);
		icons.put("potion", Material.SPLASH_POTION);
		icons.put("pot_pvp", Material.SPLASH_POTION);
		icons.put("nodebuff", Material.POTION);
		icons.put("soup", Material.MUSHROOM_STEW);
		icons.put("soup_pvp", Material.MUSHROOM_STEW);
		icons.put("boxing", Material.IRON_INGOT);
		icons.put("sumo", Material.PISTON);
		icons.put("smp", Material.GRASS_BLOCK);
		icons.put("nethop", Material.NETHERRACK);
		icons.put("nether", Material.NETHERRACK);
		icons.put("vanilla", Material.DIAMOND_SWORD);
		icons.put("overall", Material.NETHER_STAR);
		return Collections.unmodifiableMap(icons);
	}

	// ── Public getters: Battle GUI ──────────────────────────────────────────

	public Component battleTitle()          { return battleTitle; }
	public int battleRows()                 { return battleRows; }
	public int battleSize()                 { return battleRows * 9; }

	public Material titleMaterial()         { return titleMaterial; }
	public Component titleQueueName()       { return titleQueueName; }
	public Component titleChallengeName()   { return titleChallengeName; }
	public int titleSlot()                  { return titleSlot; }

	public int modeSlotStart()              { return modeSlotStart; }
	public int modeSlotEnd()                { return modeSlotEnd; }
	public String modeNamePrefix()          { return modeNamePrefix; }
	public String queueCountLine()          { return queueCountLine; }
	public String clickLine()               { return clickLine; }

	public Material queueTabMaterial()      { return queueTabMaterial; }
	public Component queueTabActive()       { return queueTabActive; }
	public Component queueTabInactive()     { return queueTabInactive; }
	public int queueTabSlot()               { return queueTabSlot; }

	public Material challengeTabMaterial()  { return challengeTabMaterial; }
	public Component challengeTabActive()   { return challengeTabActive; }
	public Component challengeTabInactive() { return challengeTabInactive; }
	public int challengeTabSlot()           { return challengeTabSlot; }

	public Material activeBattlesMaterial()        { return activeBattlesMaterial; }
	public Component activeBattlesName()           { return activeBattlesName; }
	public String activeBattlesLoreFormat()        { return activeBattlesLoreFormat; }
	public int activeBattlesSlot()                 { return activeBattlesSlot; }

	public Material leaveMaterial()         { return leaveMaterial; }
	public Component leaveName()            { return leaveName; }
	public List<Component> leaveLore()      { return leaveLore; }
	public int leaveSlot()                  { return leaveSlot; }

	public Material closeMaterial()         { return closeMaterial; }
	public Component closeName()            { return closeName; }
	public int closeSlot()                  { return closeSlot; }

	public Material iconFor(String modeId) {
		Material m = modeIcons.get(modeId.toLowerCase());
		return m != null ? m : defaultIcon;
	}

	public Material defaultIcon()           { return defaultIcon; }

	// ── Public getters: Challenge GUI ───────────────────────────────────────

	public Component challengeTitle()       { return challengeTitle; }
	public int challengeRows()              { return challengeRows; }
	public int challengeSize()              { return challengeRows * 9; }

	public Material acceptMaterial()        { return acceptMaterial; }
	public String acceptNameFormat()        { return acceptNameFormat; }
	public List<String> acceptLoreFormat()  { return acceptLoreFormat; }
	public int acceptSlot()                 { return acceptSlot; }

	public Material infoMaterial()          { return infoMaterial; }
	public String infoNameFormat()          { return infoNameFormat; }
	public List<String> infoLoreFormat()    { return infoLoreFormat; }
	public int infoSlot()                   { return infoSlot; }

	public Material declineMaterial()       { return declineMaterial; }
	public String declineNameFormat()       { return declineNameFormat; }
	public List<String> declineLoreFormat() { return declineLoreFormat; }
	public int declineSlot()                { return declineSlot; }

	public int challengeTimeoutSeconds()    { return challengeTimeoutSeconds; }

	// ── Public getters: Confirmation GUI ────────────────────────────────────

	public Component confirmationTitle()        { return confirmationTitle; }
	public int confirmationRows()               { return confirmationRows; }
	public int confirmationSize()               { return confirmationRows * 9; }

	public Material confirmInfoMaterial()        { return confirmInfoMaterial; }
	public Component confirmInfoName()           { return confirmInfoName; }
	public List<Component> confirmInfoLore()     { return confirmInfoLore; }
	public int confirmInfoSlot()                 { return confirmInfoSlot; }

	public Material confirmYesMaterial()         { return confirmYesMaterial; }
	public Component confirmYesName()            { return confirmYesName; }
	public List<Component> confirmYesLore()      { return confirmYesLore; }
	public int confirmYesSlot()                  { return confirmYesSlot; }

	public Material confirmCancelMaterial()      { return confirmCancelMaterial; }
	public Component confirmCancelName()         { return confirmCancelName; }
	public List<Component> confirmCancelLore()   { return confirmCancelLore; }
	public int confirmCancelSlot()               { return confirmCancelSlot; }

	/** Convenience: parses a raw format string with '&' codes into a Component. */
	public static Component parseColour(String raw) {
		return parse(raw);
	}

	/** Convenience: replaces placeholders then parses colour codes. */
	public static Component parseWithReplacements(String format, Map<String, String> replacements) {
		String result = format;
		for (var entry : replacements.entrySet()) {
			result = result.replace(entry.getKey(), entry.getValue());
		}
		return parse(result);
	}
}
