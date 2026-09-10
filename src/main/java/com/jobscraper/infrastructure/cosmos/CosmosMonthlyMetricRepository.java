package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.metrics.MonthlyMetric;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of {@link MonthlyMetricRepository} using Azure Cosmos DB.
 *
 * <p>Items are partitioned by {@code /month}, per the README's monthly-metrics container design.</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosMonthlyMetricRepository implements MonthlyMetricRepository {

    private final CosmosContainer container;

    public CosmosMonthlyMetricRepository(@Qualifier("monthlyMetricsContainer") CosmosContainer container) {
        this.container = container;
    }

    @Override
    public Optional<MonthlyMetric> findById(String id, String month) {
        try {
            MonthlyMetric metric = container.readItem(id, new PartitionKey(month), MonthlyMetric.class).getItem();
            return Optional.ofNullable(metric);
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public void upsert(MonthlyMetric metric) {
        container.upsertItem(metric, new PartitionKey(metric.month()), new CosmosItemRequestOptions());
    }
}

