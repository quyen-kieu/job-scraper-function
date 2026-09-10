package com.jobscraper.application.metrics;

/**
 * Contract for publishing {@code job.metrics.monthly} events.
 */
public interface MonthlyMetricPublisher {

    void publish(MonthlyMetricPublishedEvent event);
}

