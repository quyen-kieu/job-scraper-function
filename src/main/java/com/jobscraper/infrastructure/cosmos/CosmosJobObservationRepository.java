package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.jobscraper.domain.job.JobObservation;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link JobObservationRepository} using Azure Cosmos DB.
 *
 * <p>Upserts are partitioned by {@code /companyId}, consistent with the {@code job-postings}
 * container's partition key.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosJobObservationRepository implements JobObservationRepository {

    private final CosmosContainer container;

    public CosmosJobObservationRepository(@Qualifier("jobObservationsContainer") CosmosContainer container) {
        this.container = container;
    }

    @Override
    public void upsert(JobObservation observation) {
        container.upsertItem(
                observation,
                new PartitionKey(observation.companyId()),
                new CosmosItemRequestOptions());
    }

    @Override
    public List<JobObservation> findByCompanyAndMonth(String companyId, String month) {
        String startDate = month + "-01";
        String nextMonthStartDate = java.time.YearMonth.parse(month).plusMonths(1).atDay(1).toString();

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.companyId = @companyId AND c.scrapeDate >= @startDate AND c.scrapeDate < @nextMonthStartDate",
                List.of(
                        new SqlParameter("@companyId", companyId),
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@nextMonthStartDate", nextMonthStartDate)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                .setPartitionKey(new PartitionKey(companyId));

        return container.queryItems(querySpec, options, JobObservation.class)
                .stream()
                .toList();
    }
}
