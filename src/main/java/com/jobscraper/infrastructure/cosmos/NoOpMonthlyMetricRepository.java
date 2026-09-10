package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.metrics.MonthlyMetric;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * No-op implementation of {@link MonthlyMetricRepository} for local/dev environments.
 */
@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMonthlyMetricRepository implements MonthlyMetricRepository {

    @Override
    public Optional<MonthlyMetric> findById(String id, String month) {
        return Optional.empty();
    }

    @Override
    public void upsert(MonthlyMetric metric) {
        // No-op: do nothing. Useful for local development without Cosmos DB.
    }
}

