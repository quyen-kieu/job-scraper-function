package com.jobscraper.application.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Event payload consumed from {@code notifications.daily}.
 *
 * <p>Published once per company scrape run by
 * {@link com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler}, carrying
 * the full list of scraped postings so the email can list them.</p>
 *
 * @param companyId the scraped company, or {@code null} for messages produced before this field existed
 * @param postings the scraped postings; empty for messages produced before this field existed
 */
public record DailyNotificationRequestedEvent(
        String runId,
        LocalDate summaryDate,
        int companiesProcessed,
        int newCount,
        int changedCount,
        int inactiveCount,
        List<String> failures,
        String companyId,
        List<NotificationPosting> postings) {

    /**
     * Creates a counts-only event with no company ID or posting list.
     */
    public DailyNotificationRequestedEvent(
            String runId,
            LocalDate summaryDate,
            int companiesProcessed,
            int newCount,
            int changedCount,
            int inactiveCount,
            List<String> failures) {
        this(runId, summaryDate, companiesProcessed, newCount, changedCount, inactiveCount, failures,
                null, List.of());
    }

    public DailyNotificationRequestedEvent {
        postings = postings == null ? List.of() : List.copyOf(postings);
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

