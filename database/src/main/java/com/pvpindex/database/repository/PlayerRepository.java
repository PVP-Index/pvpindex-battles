package com.pvpindex.database.repository;

import com.pvpindex.database.model.PlayerProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerRepository {

    CompletableFuture<PlayerProfile> getPlayer(UUID uuid);

    CompletableFuture<Void> savePlayer(PlayerProfile profile);

    CompletableFuture<Integer> getElo(UUID uuid, String modeId);

    CompletableFuture<Void> updateElo(UUID uuid, String modeId, int newElo);
}
