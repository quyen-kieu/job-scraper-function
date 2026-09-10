package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.metrics.MonthlyMetric;

import java.util.Optional;

/**
 * Contract for persisting monthly metric aggregate documents.
 */
public interface MonthlyMetricRepository {

    Optional<MonthlyMetric> findById(String id, String month);

    void upsert(MonthlyMetric metric);
}

