package com.pvpindex.api;

public record PluginInfo(
		String name,
		String version,
		PlatformType platform
) {
	public static final String PLUGIN_ID = "pvpindex";
	public static final String CHANNEL = "pvpindex:proxy";
}
