package com.pvpindex.battles.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileStorageService {
    private final Path dataFolder;
    private final ObjectMapper objectMapper;

    public FileStorageService(Path dataFolder, ObjectMapper objectMapper) {
        this.dataFolder = dataFolder;
        this.objectMapper = objectMapper;
    }

    public Path battlesDir() { return dataFolder.resolve("battles"); }
    public Path replaysDir() { return dataFolder.resolve("replays"); }
    public Path failedSubmissionsDir() { return dataFolder.resolve("failed-submissions"); }

    public void initialise() throws IOException {
        Files.createDirectories(battlesDir());
        Files.createDirectories(replaysDir());
        Files.createDirectories(failedSubmissionsDir());
    }

    public Path saveBattlePayload(UUID battleUuid, Map<String, Object> payload) throws IOException {
        Path file = battlesDir().resolve(battleUuid + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        return file;
    }

    public Path saveFailedSubmission(UUID battleUuid, Map<String, Object> payload) throws IOException {
        Path file = failedSubmissionsDir().resolve(battleUuid + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        return file;
    }

    /**
     * Write an empty sentinel file next to the local battle payload that marks
     * it as successfully submitted.  Used by
     * {@link #listUnsubmittedBattles()} to detect crashes-before-confirmation.
     */
    public void markSubmitted(UUID battleUuid) throws IOException {
        Path marker = battlesDir().resolve(battleUuid + ".submitted");
        Files.createFile(marker);
    }

    /** Returns true when {@link #markSubmitted(UUID)} has been called for this uuid. */
    public boolean isSubmitted(UUID battleUuid) {
        return Files.exists(battlesDir().resolve(battleUuid + ".submitted"));
    }

    /**
     * Lists all local battle payload files that have NOT yet been confirmed as
     * submitted (no sibling {@code .submitted} sentinel).  These are candidates
     * for on-startup sync.
     */
    public List<Path> listUnsubmittedBattles() throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.exists(battlesDir())) return files;
        try (var stream = Files.list(battlesDir())) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .filter(p -> !Files.exists(p.resolveSibling(
                          p.getFileName().toString().replace(".json", ".submitted"))))
                  .forEach(files::add);
        }
        return files;
    }

    public List<Path> listFailedSubmissions() throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.exists(failedSubmissionsDir())) {
            return files;
        }
        try (var stream = Files.list(failedSubmissionsDir())) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(files::add);
        }
        return files;
    }

    public Map<String, Object> readJson(Path file) throws IOException {
        return objectMapper.readValue(file.toFile(), Map.class);
    }

    public void delete(Path file) throws IOException {
        Files.deleteIfExists(file);
    }
}
