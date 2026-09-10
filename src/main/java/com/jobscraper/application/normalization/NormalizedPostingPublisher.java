package com.jobscraper.application.normalization;

import com.jobscraper.domain.job.NormalizedJobPosting;

/**
 * Contract for publishing {@code job.postings.normalized} events to Kafka.
 */
public interface NormalizedPostingPublisher {

    /**
     * Publishes a normalized job posting event.
     *
     * @param posting the posting to publish
     */
    void publish(NormalizedJobPosting posting);
}

