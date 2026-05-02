package com.pvpindex.battles.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageService {
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
	private static final String[] BUNDLED_LANGUAGES = {"en", "nl", "de", "pl", "zh", "es"};

	private final JavaPlugin plugin;
	private YamlConfiguration messages;
	private YamlConfiguration fallback;

	public MessageService(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void reload() {
		String langCode = plugin.getConfig().getString("language", "en");
		File langDir = new File(plugin.getDataFolder(), "lang");
		if (!langDir.exists()) langDir.mkdirs();

		for (String code : BUNDLED_LANGUAGES) {
			saveLanguageIfAbsent(code, langDir);
		}

		File primary = new File(langDir, langCode + ".yml");
		if (!primary.exists()) {
			plugin.getLogger().warning("Language file lang/" + langCode + ".yml not found, falling back to English.");
			primary = new File(langDir, "en.yml");
		}
		messages = YamlConfiguration.loadConfiguration(primary);

		File fallbackFile = new File(langDir, "en.yml");
		fallback = fallbackFile.equals(primary)
				? messages
				: YamlConfiguration.loadConfiguration(fallbackFile);
	}

	/**
	 * Send a prefixed, localized message to a sender.
	 * Tokens are pairs: "%key1%", "value1", "%key2%", "value2", ...
	 */
	public void send(CommandSender sender, String key, String... tokens) {
		Component prefix = color(get("prefix"));
		Component body = color(replace(get(key), tokens));
		sender.sendMessage(prefix.append(body));
	}

	/**
	 * Send a localized message WITHOUT the prefix.
	 */
	public void sendRaw(CommandSender sender, String key, String... tokens) {
		sender.sendMessage(color(replace(get(key), tokens)));
	}

	/**
	 * Get the raw &-code string for a key (with token replacement), without parsing or sending.
	 */
	public String raw(String key, String... tokens) {
		return replace(get(key), tokens);
	}

	/**
	 * Get a parsed Component for a key (with token replacement). No prefix.
	 */
	public Component component(String key, String... tokens) {
		return color(replace(get(key), tokens));
	}

	private String get(String key) {
		String value = messages.getString(key);
		if (value == null && fallback != messages) {
			value = fallback.getString(key);
		}
		return value != null ? value : key;
	}

	private static String replace(String input, String... tokens) {
		if (tokens.length < 2) return input;
		String result = input;
		for (int i = 0; i < tokens.length - 1; i += 2) {
			result = result.replace(tokens[i], tokens[i + 1]);
		}
		return result;
	}

	private static Component color(String input) {
		return LEGACY.deserialize(input);
	}

	private void saveLanguageIfAbsent(String code, File langDir) {
		File target = new File(langDir, code + ".yml");
		if (target.exists()) return;

		String resourcePath = "lang/" + code + ".yml";
		try (InputStream in = plugin.getResource(resourcePath)) {
			if (in == null) return;
			try (OutputStream out = Files.newOutputStream(target.toPath())) {
				in.transferTo(out);
			}
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save language file " + resourcePath + ": " + e.getMessage());
		}
	}
}
