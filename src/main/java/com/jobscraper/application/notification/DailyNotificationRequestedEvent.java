package com.jobscraper.application.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Event payload consumed from {@code notifications.daily}.
 */
public record DailyNotificationRequestedEvent(
        String runId,
        LocalDate summaryDate,
        int companiesProcessed,
        int newCount,
        int changedCount,
        int inactiveCount,
        List<String> failures) {

    public DailyNotificationRequestedEvent {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(summaryDate, "summaryDate must not be null");
        Objects.requireNonNull(failures, "failures must not be null");

        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }

        if (companiesProcessed < 0 || newCount < 0 || changedCount < 0 || inactiveCount < 0) {
            throw new IllegalArgumentException("Count fields must be non-negative");
        }
    }
}

