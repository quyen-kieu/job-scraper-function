package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.job.JobObservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * No-op implementation of {@link JobObservationRepository} for local/dev environments.
 *
 * <p>Used when Azure Cosmos DB is disabled. Simply does nothing, allowing the full
 * pipeline to be exercised without external dependencies.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpJobObservationRepository implements JobObservationRepository {

    @Override
    public void upsert(JobObservation observation) {
        // No-op: do nothing. Useful for local development without Cosmos DB.
    }

    @Override
    public List<JobObservation> findByCompanyAndMonth(String companyId, String month) {
        return List.of();
    }
}
