package com.jobscraper.scraper.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable context for a single company scrape operation.
 *
 * <p>Passed to each {@link CompanyScraper} to provide run correlation and timing.</p>
 */
public record ScrapeContext(String runId, String companyId, Instant requestedAt) {

    public ScrapeContext {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");

        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }
    }
}

