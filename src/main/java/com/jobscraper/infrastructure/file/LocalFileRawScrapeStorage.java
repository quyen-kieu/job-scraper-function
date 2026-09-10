package com.jobscraper.infrastructure.file;

import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Implementation of {@link RawScrapeStorage} that writes to the local filesystem.
 *
 * <p>Intended for local development and manual scrape runs (e.g. {@code LocalMcKessonScrapeRunner})
 * before Blob Storage is wired up. Uses the same path layout documented in the README:</p>
 *
 * <pre>
 * {root}/{companyId}/{yyyy}/{MM}/{dd}/{runId}/search-page-{n}.json
 * {root}/{companyId}/{yyyy}/{MM}/{dd}/{runId}/job-{externalJobId}.html
 * {root}/{companyId}/{yyyy}/{MM}/{dd}/{runId}/run-summary.json
 * </pre>
 *
 * <p>Active only when {@code raw-storage.mode=file}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "raw-storage", name = "mode", havingValue = "file")
public class LocalFileRawScrapeStorage implements RawScrapeStorage {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path rootPath;

    public LocalFileRawScrapeStorage(RawStorageProperties properties) {
        this.rootPath = Path.of(properties.fileRootPath());
    }

    @Override
    public String writeSearchPage(String companyId, String runId, int pageNumber, String json) throws IOException {
        Path path = runDirectory(companyId, runId).resolve("search-page-" + pageNumber + ".json");
        return write(path, json);
    }

    @Override
    public String writeJobDetail(String companyId, String runId, String externalJobId, String html) throws IOException {
        Path path = runDirectory(companyId, runId).resolve("job-" + externalJobId + ".html");
        return write(path, html);
    }

    @Override
    public String writeRunSummary(String companyId, String runId, String json) throws IOException {
        Path path = runDirectory(companyId, runId).resolve("run-summary.json");
        return write(path, json);
    }

    private Path runDirectory(String companyId, String runId) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        return rootPath.resolve(companyId).resolve(datePart).resolve(runId);
    }

    private String write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toAbsolutePath().toString();
    }
}

