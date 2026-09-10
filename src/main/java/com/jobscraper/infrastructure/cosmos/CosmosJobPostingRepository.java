package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of {@link JobPostingRepository} using Azure Cosmos DB.
 *
 * <p>Upserts are partitioned by {@code /companyId}, matching the container's partition key
 * definition documented in the README's {@code job-postings} section.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosJobPostingRepository implements JobPostingRepository {

    private final CosmosContainer container;

    public CosmosJobPostingRepository(@Qualifier("jobPostingsContainer") CosmosContainer container) {
        this.container = container;
    }

    @Override
    public void upsert(NormalizedJobPosting posting) {
        container.upsertItem(
                posting,
                new PartitionKey(posting.companyId()),
                new CosmosItemRequestOptions());
    }

    @Override
    public Optional<NormalizedJobPosting> findById(String id, String companyId) {
        try {
            NormalizedJobPosting existing =
                    container.readItem(id, new PartitionKey(companyId), NormalizedJobPosting.class).getItem();
            return Optional.ofNullable(existing);
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }
}

