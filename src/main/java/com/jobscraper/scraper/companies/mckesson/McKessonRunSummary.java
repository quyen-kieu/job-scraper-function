package com.jobscraper.scraper.companies.mckesson;

import com.jobscraper.scraper.api.ScrapedJob;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Structured run-summary content written via {@code RawScrapeStorage.writeRunSummary}
 * for one McKesson scrape run.
 *
 * <p>This is the human-readable result file written alongside raw search and detail responses.</p>
 */
public record McKessonRunSummary(
        String runId,
        String companyId,
        Instant startedAt,
        Instant completedAt,
        int searchPagesFetched,
        int detailPagesFetched,
        List<ScrapedJob> jobs,
        List<String> errors) {

    public McKessonRunSummary {
        Objects.requireNonNull(jobs, "jobs must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        jobs = List.copyOf(jobs);
        errors = List.copyOf(errors);
    }
}
