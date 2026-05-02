package com.pvpindex.battles.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.config.PluginSettings;
import com.pvpindex.battles.moderation.BanEntry;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class PvPIndexApiClient {
    private final PluginSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Logger logger;

    public PvPIndexApiClient(PluginSettings settings, ObjectMapper objectMapper) {
        this(settings, objectMapper, Logger.getLogger("PvPIndexBattles"));
    }

    public PvPIndexApiClient(PluginSettings settings, ObjectMapper objectMapper, Logger logger) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.timeoutSeconds())).build();
    }

    /** Result of a single HTTP attempt. {@link #retryable()} hints whether a caller should retry. */
    public record PostResult(boolean ok, int statusCode, String body, String error) {
        public static PostResult success(int code) { return new PostResult(true, code, null, null); }
        public static PostResult httpError(int code, String body) { return new PostResult(false, code, body, null); }
        public static PostResult exception(String message) { return new PostResult(false, 0, null, message); }
        /** Network errors and 5xx are retryable; 4xx (client errors, validation, auth) are not. */
        public boolean retryable() {
            if (ok) return false;
            if (statusCode == 0) return true;
            if (statusCode >= 500) return true;
            if (statusCode == 408 || statusCode == 429) return true;
            return false;
        }
        /** Concise human-readable summary suitable for logs. */
        public String describe() {
            if (ok) return "HTTP " + statusCode;
            if (error != null) return error;
            String snippet = body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "…" : body);
            return "HTTP " + statusCode + (snippet.isEmpty() ? "" : " — " + snippet);
        }
    }

    public CompletableFuture<PostResult> submitBattle(Map<String, Object> payload) {
        return postWithResult("/api/battles", payload);
    }

    /**
     * POST a compact heartbeat payload for a batch of active battles.
     * Used by {@link com.pvpindex.battles.battle.BattleBatchScheduler} so the
     * backend can detect crashes mid-battle.
     *
     * <p>If the server returns 404 the endpoint is not yet deployed — callers
     * should treat this as a graceful no-op rather than an error.</p>
     */
    public CompletableFuture<PostResult> sendHeartbeat(List<Map<String, Object>> battles) {
        Map<String, Object> payload = Map.of("battles", battles);
        return postQuietly("/api/battles/heartbeat", payload);
    }

    public CompletableFuture<PostResult> confirmBattle(UUID battleUuid, Map<String, Object> payload) {
        return postWithResult("/api/battles/" + battleUuid + "/confirm", payload);
    }

    public CompletableFuture<PostResult> disputeBattle(UUID battleUuid, Map<String, Object> payload) {
        return postWithResult("/api/battles/" + battleUuid + "/dispute", payload);
    }

    /**
     * Verify a player's website claim code in-game.
     * Called when a player runs {@code /pvpindex verify <code>}.
     * The server's Bearer token is used as auth; the player's UUID + username are sent.
     */
    public CompletableFuture<PostResult> verifyMinecraft(UUID playerUuid, String username, String claimCode) {
        Map<String, Object> payload = Map.of(
                "player_uuid", playerUuid.toString(),
                "username",    username,
                "claim_code",  claimCode.toUpperCase()
        );
        return postWithResult("/api/verify-minecraft", payload);
    }

    /**
     * Pull the verified-server federated ban list. Servers that opt into
     * inbound enforcement honour these bans on player login.
     */
    public CompletableFuture<List<BanEntry>> fetchFederatedBans() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.apiBaseUrl() + "/api/moderation/federated-bans"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) return List.<BanEntry>of();
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<BanEntry>>() {});
                        } catch (IOException e) {
                            return List.<BanEntry>of();
                        }
                    })
                    .exceptionally(ex -> List.<BanEntry>of());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(List.<BanEntry>of());
        }
    }

    /** Publish a single federated ban issued on this server to the network. */
    public CompletableFuture<Boolean> publishFederatedBan(BanEntry entry) {
        try {
            String body = objectMapper.writeValueAsString(entry);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.apiBaseUrl() + "/api/moderation/federated-bans"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300)
                    .exceptionally(ex -> false);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Like {@link #postWithResult} but does not log warnings for non-2xx responses.
     * Used for fire-and-forget endpoints (heartbeat) where the caller handles errors.
     */
    private CompletableFuture<PostResult> postQuietly(String path, Map<String, Object> payload) {
        final String url = settings.apiBaseUrl() + path;
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int code = response.statusCode();
                        if (code >= 200 && code < 300) {
                            return PostResult.success(code);
                        }
                        String respBody = response.body() == null ? "" : response.body();
                        return PostResult.httpError(code, respBody);
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        return PostResult.exception(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    });
        } catch (IOException e) {
            return CompletableFuture.completedFuture(PostResult.exception("Serialization: " + e.getMessage()));
        }
    }

    private CompletableFuture<PostResult> postWithResult(String path, Map<String, Object> payload) {
        final String url = settings.apiBaseUrl() + path;
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int code = response.statusCode();
                        if (code >= 200 && code < 300) {
                            return PostResult.success(code);
                        }
                        String respBody = response.body() == null ? "" : response.body();
                        String snippet = respBody.length() > 500 ? respBody.substring(0, 500) + "…" : respBody;
                        logger.warning("POST " + url + " returned " + code + ": " + snippet);
                        return PostResult.httpError(code, respBody);
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        logger.warning("POST " + url + " failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                        return PostResult.exception(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    });
        } catch (IOException e) {
            logger.warning("POST " + url + " payload serialization failed: " + e.getMessage());
            return CompletableFuture.completedFuture(PostResult.exception("Serialization: " + e.getMessage()));
        }
    }

    /**
     * Fetch per-mode elo and rank for a player by Minecraft username.
     * Parses the {@code {"data": [{"game_mode":…,"rank":…,"elo":…}]}} shape
     * returned by {@code GET /api/players/{username}/rankings}.
     *
     * @return map of upper-cased mode name → {@link RankEntry}; empty on error
     */
    public CompletableFuture<Map<String, RankEntry>> fetchPlayerRankings(String username) {
        try {
            String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.apiBaseUrl() + "/api/players/" + encodedName + "/rankings"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) return Map.<String, RankEntry>of();
                        try {
                            Map<String, Object> body = objectMapper.readValue(
                                    response.body(), new TypeReference<>() {});
                            Object dataObj = body.get("data");
                            if (!(dataObj instanceof List<?> dataList)) return Map.<String, RankEntry>of();
                            Map<String, RankEntry> result = new LinkedHashMap<>();
                            for (Object item : dataList) {
                                if (!(item instanceof Map<?, ?> row)) continue;
                                String mode = String.valueOf(row.get("game_mode")).toUpperCase();
                                int elo  = ((Number) row.get("elo")).intValue();
                                int rank = ((Number) row.get("rank")).intValue();
                                result.put(mode, new RankEntry(elo, rank));
                            }
                            return result;
                        } catch (IOException | ClassCastException | NullPointerException e) {
                            return Map.<String, RankEntry>of();
                        }
                    })
                    .exceptionally(ex -> Map.of());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /** A single mode's current elo and ladder position returned by the API. */
    public record RankEntry(int elo, int rank) {}
}
