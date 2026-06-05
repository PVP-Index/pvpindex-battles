package com.pvpindex.battles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pvpindex.battles.storage.FileStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {
    @Test
    void storesAndListsFailedSubmissions() throws Exception {
        Path temp = Files.createTempDirectory("pvpindex-test");
        FileStorageService storage = new FileStorageService(temp, new ObjectMapper());
        storage.initialise();

        UUID id = UUID.randomUUID();
        storage.saveFailedSubmission(id, Map.of("battle_uuid", id.toString()));

        assertEquals(1, storage.listFailedSubmissions().size());
    }
}
