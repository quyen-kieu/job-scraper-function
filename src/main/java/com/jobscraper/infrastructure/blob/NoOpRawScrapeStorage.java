package com.jobscraper.infrastructure.blob;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link RawScrapeStorage} for local/dev environments.
 *
 * <p>Used when Azure Blob Storage is disabled and local-file storage
 * ({@code raw-storage.mode=file}) is not selected either. Simply logs and returns mock paths
 * for testing the full pipeline without external dependencies.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.storage", name = "enabled", havingValue = "false", matchIfMissing = true)
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
    public String writeRunSummary(String companyId, String runId, String json) {
        // Return a mock path without actually writing
        return String.format("mock://summary/%s/%s/run-summary", companyId, runId);
    }
}

