package com.jobscraper.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage;
import com.jobscraper.infrastructure.http.JavaHttpJobSearchClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.companies.mckesson.McKessonScraper;

import java.time.Instant;
import java.util.UUID;

/**
 * Standalone runner that executes a real McKesson scrape and writes results to local files,
 * without requiring a live Kafka broker, Azure Functions host, Blob Storage, or Cosmos DB.
 *
 * <p>This is a manual developer tool, not part of the Function App's runtime wiring. It
 * constructs its dependencies directly (no Spring context) so it can be run standalone via:</p>
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=com.jobscraper.tools.LocalMcKessonScrapeRunner
 * </pre>
 *
 * <p>Output is written under {@code ./output/raw/mckesson/{yyyy}/{MM}/{dd}/{runId}/}, matching
 * the README's documented Blob Storage path layout. Wiring this scraper to real Azure Blob
 * Storage and Cosmos DB is a separate, later step.</p>
 */
public final class LocalMcKessonScrapeRunner {

    private LocalMcKessonScrapeRunner() {
        // Entry point only
    }

    public static void main(String[] args) throws Exception {
        JobScraperProperties scraperProperties = new JobScraperProperties();
        // Defaults (max 3 search pages, 10 detail pages, 300ms delay) are intentionally
        // conservative for a first live run against a real third-party site.

        RawStorageProperties storageProperties = new RawStorageProperties();
        storageProperties.setMode("file");
        storageProperties.setFileRootPath("./output/raw");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RawScrapeStorage storage = new LocalFileRawScrapeStorage(storageProperties);
        JavaHttpJobSearchClient httpClient = new JavaHttpJobSearchClient(scraperProperties);

        McKessonScraper scraper = new McKessonScraper(httpClient, storage, objectMapper, scraperProperties);

        String runId = UUID.randomUUID().toString();
        ScrapeContext context = new ScrapeContext(runId, "mckesson", Instant.now());

        System.out.println("Starting McKesson scrape. runId=" + runId);
        System.out.println("Output root: " + java.nio.file.Path.of(storageProperties.fileRootPath()).toAbsolutePath());

        ScrapeResult result = scraper.scrape(context);

        System.out.println();
        System.out.println("Scrape complete.");
        System.out.println("  companyId:          " + result.companyId());
        System.out.println("  runId:               " + result.runId());
        System.out.println("  startedAt:           " + result.startedAt());
        System.out.println("  completedAt:         " + result.completedAt());
        System.out.println("  searchPagesFetched:  " + result.searchPagesFetched());
        System.out.println("  detailPagesFetched:  " + result.detailPagesFetched());
        System.out.println("  files written:       " + result.blobPaths().size());
        for (String path : result.blobPaths()) {
            System.out.println("    - " + path);
        }
        if (!result.errors().isEmpty()) {
            System.out.println("  errors:");
            for (String error : result.errors()) {
                System.out.println("    - " + error);
            }
        }
    }
}



