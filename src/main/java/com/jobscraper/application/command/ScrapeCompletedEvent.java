package com.jobscraper.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable event published after a company scrape completes.
 *
 * <p>This event is published to the {@code job.scrape.completed} Kafka topic
 * and consumed by downstream normalizer and other consumers.</p>
 */
public record ScrapeCompletedEvent(
        String runId,
        String companyId,
        Instant startedAt,
        Instant completedAt,
        int searchPagesFetched,
        int detailPagesFetched,
        List<String> blobPaths,
        List<String> errors) {

    public ScrapeCompletedEvent {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(blobPaths, "blobPaths must not be null");
        Objects.requireNonNull(errors, "errors must not be null");

        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }
    }
}

