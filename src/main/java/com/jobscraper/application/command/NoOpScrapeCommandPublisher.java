package com.jobscraper.application.command;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Component
@ConditionalOnMissingBean(ScrapeCommandPublisher.class)
public class NoOpScrapeCommandPublisher implements ScrapeCommandPublisher {

    @Override
    public void publish(ScrapeCompanyCommand command) {
        // Stage 1 placeholder: real Kafka publishing is added in Stage 2.
    }
}

