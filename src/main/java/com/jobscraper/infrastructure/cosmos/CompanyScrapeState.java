package com.jobscraper.infrastructure.cosmos;

import java.time.Instant;

/**
 * A per-company control document in the postings partition. It is not a job posting.
 */
public record CompanyScrapeState(String id, String companyId, String recordType,
                                 Instant watermarkAt, String watermarkRunId,
                                 Instant lastSuccessfulReconciliationAt, int inactiveCount) {
    public static final String ID = "__scrape_control__";
    public static final String TYPE = "scrape-control";

    public static CompanyScrapeState empty(String companyId) {
        return new CompanyScrapeState(ID, companyId, TYPE, null, null, null, 0);
    }

    public CompanyScrapeState begin(Instant at, String runId) {
        return new CompanyScrapeState(id, companyId, recordType, at, runId,
                lastSuccessfulReconciliationAt, 0);
    }

    public CompanyScrapeState countInactive() {
        return new CompanyScrapeState(id, companyId, recordType, watermarkAt, watermarkRunId,
                lastSuccessfulReconciliationAt, inactiveCount + 1);
    }

    public CompanyScrapeState complete() {
        return new CompanyScrapeState(id, companyId, recordType, watermarkAt, watermarkRunId,
                watermarkAt, inactiveCount);
    }
}
