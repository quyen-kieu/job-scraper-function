package com.jobscraper.application.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link ScrapeCompletedPublisher} for local/dev environments.
 *
 * <p>Used when Kafka is disabled. Simply logs and does nothing.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpScrapeCompletedPublisher implements ScrapeCompletedPublisher {

    @Override
    public void publish(ScrapeCompletedEvent event) {
        // No-op: do nothing. Useful for local development without Kafka.
    }
}

