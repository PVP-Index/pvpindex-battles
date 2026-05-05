package com.pvpindex.database.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PartyRepository {

    CompletableFuture<Void> saveParty(UUID partyId, UUID leaderId, java.util.Set<UUID> members);

    CompletableFuture<Void> deleteParty(UUID partyId);
}
