package com.jobscraper.application.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op publisher for local/dev runs without Kafka.
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMonthlyMetricPublisher implements MonthlyMetricPublisher {

    @Override
    public void publish(MonthlyMetricPublishedEvent event) {
        // No-op: do nothing. Useful for local development without Kafka.
    }
}

