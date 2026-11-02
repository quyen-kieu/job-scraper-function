package com.jobscraper.scraper.companies.oracle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Structured run-summary content written via {@code RawScrapeStorage.writeRunSummary} for one
 * Oracle Recruiting Cloud scrape run.
 */
public record OracleRunSummary(
        String runId,
        String companyId,
        String keyword,
        Instant startedAt,
        Instant completedAt,
        int searchPagesFetched,
        int detailPagesFetched,
        List<OracleRequisitionSummary> jobs,
        List<String> errors) {

    public OracleRunSummary {
        Objects.requireNonNull(jobs, "jobs must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        jobs = List.copyOf(jobs);
        errors = List.copyOf(errors);
    }
}

