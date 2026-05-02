package com.pvpindex.battles.replay;

/**
 * Settings dedicated to the snapshot/packet replay recorder. Kept separate
 * from {@link com.pvpindex.battles.config.PluginSettings} so the recorder can
 * be tuned without touching legacy battle settings (or test fixtures).
 *
 * @param tickRate    snapshots per second (1..20)
 * @param maxFrames   safety cap to avoid runaway memory on long battles
 * @param compress    write replay files with gzip compression
 * @param keepEvents  also keep the legacy {@link ReplayEvent} log
 */
public record ReplaySettings(
        ReplayDetailLevel detailLevel,
        int tickRate,
        int maxFrames,
        boolean compress,
        boolean keepEvents
) {
    public static ReplaySettings defaults() {
        return new ReplaySettings(ReplayDetailLevel.HIGH, 20, 144_000, true, true);
    }
}
