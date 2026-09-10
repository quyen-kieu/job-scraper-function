package com.jobscraper.application.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link DailyNotificationPublisher} for local/dev environments.
 *
 * <p>Used when Kafka is disabled. Simply does nothing, allowing the full pipeline to be
 * exercised without external dependencies.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDailyNotificationPublisher implements DailyNotificationPublisher {

    @Override
    public void publish(DailyNotificationRequestedEvent event) {
        // No-op: do nothing. Useful for local development without Kafka.
    }
}

