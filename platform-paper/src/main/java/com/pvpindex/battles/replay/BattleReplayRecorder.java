package com.pvpindex.battles.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.battle.BattleSession;
import com.pvpindex.battles.event.PvPIndexReplayRecordEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class BattleReplayRecorder {
    private final Plugin plugin;
    private final ObjectMapper objectMapper;
    private final ReplayDetailLevel detailLevel;
    private final Map<UUID, Long> startTimes = new LinkedHashMap<>();
    private final Map<UUID, List<ReplayEvent>> events = new LinkedHashMap<>();

    public BattleReplayRecorder(Plugin plugin, ObjectMapper objectMapper, ReplayDetailLevel detailLevel) {
        this.plugin = plugin;
        this.objectMapper = objectMapper;
        this.detailLevel = detailLevel;
    }

    public void start(BattleSession session) {
        startTimes.put(session.getUuid(), System.currentTimeMillis());
        events.put(session.getUuid(), new ArrayList<>());
    }

    public void record(BattleSession session, String type, UUID actor, UUID target, Map<String, Object> data) {
        if (!events.containsKey(session.getUuid())) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startTimes.get(session.getUuid());
        ReplayEvent replayEvent = new ReplayEvent(elapsed, type, actor, target, data);
        events.get(session.getUuid()).add(replayEvent);
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(new PvPIndexReplayRecordEvent(session, replayEvent));
        }
    }

    public Map<String, Object> buildReplay(BattleSession session) {
        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("version", 1);
        replay.put("detail_level", detailLevel.name());
        replay.put("battle_uuid", session.getUuid().toString());
        replay.put("started_at", session.getStartedAt() != null ? session.getStartedAt().toString() : Instant.now().toString());
        replay.put("ended_at", session.getEndedAt() != null ? session.getEndedAt().toString() : Instant.now().toString());
        replay.put("events", events.getOrDefault(session.getUuid(), List.of()));
        return replay;
    }

    public Path writeReplay(Path replaysDirectory, BattleSession session) throws IOException {
        Files.createDirectories(replaysDirectory);
        Path replayFile = replaysDirectory.resolve(session.getUuid() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(replayFile.toFile(), buildReplay(session));
        return replayFile;
    }
}
