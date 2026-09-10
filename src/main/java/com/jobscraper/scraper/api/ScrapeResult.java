package com.jobscraper.scraper.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a single company scrape operation.
 *
 * <p>Returned by {@link CompanyScraper#scrape(ScrapeContext)} and published as a
 * {@code job.scrape.completed} event to Kafka.</p>
 */
public record ScrapeResult(
        String runId,
        String companyId,
        Instant startedAt,
        Instant completedAt,
        int searchPagesFetched,
        int detailPagesFetched,
        List<String> blobPaths,
        List<String> errors) {

    public ScrapeResult {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(blobPaths, "blobPaths must not be null");
        Objects.requireNonNull(errors, "errors must not be null");

        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }

        if (searchPagesFetched < 0) {
            throw new IllegalArgumentException("searchPagesFetched must be non-negative");
        }

        if (detailPagesFetched < 0) {
            throw new IllegalArgumentException("detailPagesFetched must be non-negative");
        }
    }
}

