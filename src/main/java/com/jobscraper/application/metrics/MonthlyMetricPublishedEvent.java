package com.jobscraper.application.metrics;

import java.time.Instant;
import java.util.Objects;

/**
 * Event payload published to {@code job.metrics.monthly} after successful metric updates.
 */
public record MonthlyMetricPublishedEvent(
        String companyId,
        String month,
        String normalizedTitle,
        String jobLevel,
        int postingCount,
        int uniquePostingCount,
        Instant lastCalculatedAt) {

    public MonthlyMetricPublishedEvent {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(normalizedTitle, "normalizedTitle must not be null");
        Objects.requireNonNull(jobLevel, "jobLevel must not be null");
        Objects.requireNonNull(lastCalculatedAt, "lastCalculatedAt must not be null");
    }
}

