package com.pvpindex.battles.api;

import com.pvpindex.battles.battle.BattleParticipant;
import com.pvpindex.battles.battle.BattleSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattlePayloadFactory {

    /**
     * Build the API payload without start-setup data. Delegates to the full
     * overload with {@code null} for backward compatibility.
     */
    public Map<String, Object> toPayload(BattleSession session, Map<String, Object> replayData, String replayUrl) {
        return toPayload(session, replayData, replayUrl, null);
    }

    /**
     * Build the API payload, optionally embedding per-player battle-start
     * equipment/health snapshots inside each participant entry.
     *
     * @param startSetupMaps nullable; keyed by player UUID, values are the
     *                       serialised setup map from
     *                       {@code BattleStartSetupService#toApiMap}
     */
    public Map<String, Object> toPayload(
            BattleSession session,
            Map<String, Object> replayData,
            String replayUrl,
            Map<UUID, Map<String, Object>> startSetupMaps) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uuid", session.getUuid().toString());
        payload.put("server_id", session.getServerId());
        payload.put("game_mode_slug", session.getGameMode().name().toLowerCase());
        payload.put("battle_type", session.getBattleType().name());
        payload.put("status", session.getStatus().name());
        payload.put("participants", session.getParticipants().stream()
                .map(p -> participantMap(p, startSetupMaps))
                .toList());
        payload.put("winners", session.getWinners().stream().map(Object::toString).toList());
        payload.put("losers",  session.getLosers().stream().map(Object::toString).toList());
        payload.put("started_at", session.getStartedAt() == null ? null : session.getStartedAt().toString());
        payload.put("ended_at",   session.getEndedAt()   == null ? null : session.getEndedAt().toString());
        payload.put("replay_data", replayData);
        payload.put("replay_url",  replayUrl);
        payload.put("metadata",    session.getMetadata());
        return payload;
    }

    /**
     * Normalises payloads that were persisted by older plugin versions before
     * the field-name rename. Safe to call on already-normalised payloads.
     *
     * Legacy renames:
     *   battle_uuid         → uuid
     *   game_mode (UPPER)   → game_mode_slug (lower)
     *   participants[].uuid → participants[].minecraft_uuid
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeLegacyPayload(Map<String, Object> payload) {
        Map<String, Object> p = new LinkedHashMap<>(payload);

        // battle_uuid → uuid
        if (!p.containsKey("uuid") && p.containsKey("battle_uuid")) {
            p.put("uuid", p.remove("battle_uuid"));
        }

        // game_mode (e.g. "UHC") → game_mode_slug (e.g. "uhc")
        if (!p.containsKey("game_mode_slug") && !p.containsKey("game_mode_id") && p.containsKey("game_mode")) {
            Object gm = p.remove("game_mode");
            p.put("game_mode_slug", gm instanceof String s ? s.toLowerCase() : gm);
        }

        // participants[].uuid → participants[].minecraft_uuid
        if (p.containsKey("participants")) {
            Object raw = p.get("participants");
            if (raw instanceof List<?> list) {
                List<Object> normalized = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> pm) {
                        Map<String, Object> np = new LinkedHashMap<>((Map<String, Object>) pm);
                        if (!np.containsKey("minecraft_uuid") && np.containsKey("uuid")) {
                            np.put("minecraft_uuid", np.remove("uuid"));
                        }
                        normalized.add(np);
                    } else {
                        normalized.add(item);
                    }
                }
                p.put("participants", normalized);
            }
        }

        return p;
    }

    private Map<String, Object> participantMap(BattleParticipant participant) {
        return participantMap(participant, null);
    }

    private Map<String, Object> participantMap(
            BattleParticipant participant,
            Map<UUID, Map<String, Object>> startSetupMaps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minecraft_uuid",     participant.getUuid().toString());
        map.put("minecraft_username", participant.getMinecraftUsername());
        map.put("team",               participant.getTeam());
        // Map ParticipantResult values to API result strings.
        // LEFT means the player forfeited/surrendered; all other values use
        // their lower-cased name (win, loss, draw, etc.).
        String resultStr = switch (participant.getResult()) {
            case LEFT -> "surrender";
            default   -> participant.getResult().name().toLowerCase();
        };
        map.put("result",        resultStr);
        map.put("kills",         participant.getKills());
        map.put("deaths",        participant.getDeaths());
        map.put("damage_dealt",  participant.getDamageDealt());
        map.put("damage_taken",  participant.getDamageTaken());
        map.put("healing_done",  participant.getHealingDone());
        map.put("elo_before",    participant.getEloBefore());
        map.put("elo_after",     participant.getEloAfter());
        map.put("elo_change",    participant.getEloChange());
        if (startSetupMaps != null) {
            Map<String, Object> setup = startSetupMaps.get(participant.getUuid());
            if (setup != null) {
                map.put("start_setup", setup);
            }
        }
        return map;
    }
}
