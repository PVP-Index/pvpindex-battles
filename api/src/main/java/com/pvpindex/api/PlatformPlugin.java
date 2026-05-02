package com.pvpindex.api;

/**
 * Common lifecycle contract implemented by both the Paper and Velocity
 * bootstrap modules.
 */
public interface PlatformPlugin {
	void onPlatformEnable();
	void onPlatformDisable();
	PlatformType getPlatformType();
	PluginInfo getPluginInfo();
}
