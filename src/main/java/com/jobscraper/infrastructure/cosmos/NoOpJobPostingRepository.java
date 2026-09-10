package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.job.NormalizedJobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * No-op implementation of {@link JobPostingRepository} for local/dev environments.
 *
 * <p>Used when Azure Cosmos DB is disabled. Simply does nothing, allowing the full
 * pipeline to be exercised without external dependencies.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpJobPostingRepository implements JobPostingRepository {

    @Override
    public void upsert(NormalizedJobPosting posting) {
        // No-op: do nothing. Useful for local development without Cosmos DB.
    }

    @Override
    public Optional<NormalizedJobPosting> findById(String id, String companyId) {
        // No-op: always report "not found", so local runs classify every posting as NEW
        // for Stage 6 daily-summary purposes.
        return Optional.empty();
    }
}

