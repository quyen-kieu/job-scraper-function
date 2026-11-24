package com.jobscraper.infrastructure.storage;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link RawScrapeStorage} for local/dev environments.
 *
 * <p>Used when neither local files nor Cosmos handoffs are selected. Raw writes return mock
 * paths; description operations fail explicitly.</p>
 */
@Component
@Conditional(NotFileRawStorageModeCondition.class)
public class NoOpRawScrapeStorage implements RawScrapeStorage {

    @Override
    public String writeSearchPage(String companyId, String runId, int pageNumber, String json) {
        // Return a mock path without actually writing
        return String.format("mock://search/%s/%s/page-%d", companyId, runId, pageNumber);
    }

    @Override
    public String writeJobDetail(String companyId, String runId, String externalJobId, String html) {
        // Return a mock path without actually writing
        return String.format("mock://detail/%s/%s/job-%s", companyId, runId, externalJobId);
    }

    @Override
    public String writeDescription(String companyId, String runId, String externalJobId, String text) {
        throw new IllegalStateException("Description storage requires Cosmos DB or file mode");
    }

    @Override
    public String readDescription(String companyId, String externalJobId, String path) {
        throw new IllegalStateException("Description storage requires Cosmos DB or file mode");
    }

    @Override
    public String writeRunSummary(String companyId, String runId, String json) {
        // Return a mock path without actually writing
        return String.format("mock://summary/%s/%s/run-summary", companyId, runId);
    }
}
