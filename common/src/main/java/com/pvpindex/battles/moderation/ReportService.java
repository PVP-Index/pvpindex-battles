package com.pvpindex.battles.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory + JSON-backed report store. Backed by a single
 * {@code reports.json} so it survives restarts; small enough that we don't
 * need a real database. Larger deployments should swap this for the API
 * client.
 */
public final class ReportService {
    private final ObjectMapper mapper;
    private final Path file;
    private final List<ReportEntry> reports = new CopyOnWriteArrayList<>();

    public ReportService(ObjectMapper mapper, Path dataDir) {
        this.mapper = mapper;
        this.file = dataDir.resolve("reports.json");
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) return;
        reports.clear();
        reports.addAll(mapper.readValue(file.toFile(), new TypeReference<ArrayList<ReportEntry>>() {}));
    }

    public synchronized void persist() throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), reports);
    }

    public ReportEntry submit(ReportEntry entry) throws IOException {
        reports.add(entry);
        persist();
        return entry;
    }

    public List<ReportEntry> openReports() {
        return reports.stream().filter(r -> r.status() == ReportEntry.ReportStatus.OPEN).toList();
    }

    public List<ReportEntry> all() { return List.copyOf(reports); }

    public synchronized void updateStatus(UUID reportId, ReportEntry.ReportStatus status) throws IOException {
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).id().equals(reportId)) {
                reports.set(i, reports.get(i).withStatus(status));
                persist();
                return;
            }
        }
    }
}
