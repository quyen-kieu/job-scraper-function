package com.jobscraper.scraper.companies.mckesson;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Structured run-summary content written via {@code RawScrapeStorage.writeRunSummary}
 * for one McKesson scrape run.
 *
 * <p>This is the primary human-readable "result file" produced by a scrape run before
 * Blob Storage/Cosmos DB persistence is wired up.</p>
 */
public record McKessonRunSummary(
        String runId,
        String companyId,
        Instant startedAt,
        Instant completedAt,
        int searchPagesFetched,
        int detailPagesFetched,
        List<McKessonJobSummary> jobs,
        List<String> errors) {

    public McKessonRunSummary {
        Objects.requireNonNull(jobs, "jobs must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        jobs = List.copyOf(jobs);
        errors = List.copyOf(errors);
    }
}

