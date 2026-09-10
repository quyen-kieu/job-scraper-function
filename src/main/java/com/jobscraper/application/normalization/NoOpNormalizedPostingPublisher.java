package com.jobscraper.application.normalization;

import com.jobscraper.domain.job.NormalizedJobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link NormalizedPostingPublisher} for local/dev environments.
 *
 * <p>Used when Kafka is disabled. Simply does nothing.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpNormalizedPostingPublisher implements NormalizedPostingPublisher {

    @Override
    public void publish(NormalizedJobPosting posting) {
        // No-op: do nothing. Useful for local development without Kafka.
    }
}

