package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One scraped job posting as listed in a {@link DailyNotificationRequestedEvent} email.
 *
 * @param externalJobId the company site's stable job identifier
 * @param title the job title
 * @param location the job location
 * @param datePosted the posting date, or {@code null} if unknown
 * @param url the public job URL, or {@code null} if unknown
 * @param salaryRange the salary range from the job description, or {@code null} if unknown
 * @param outcome whether the posting is new, changed, or unchanged since it was last stored
 */
public record NotificationPosting(
        String externalJobId,
        String title,
        String location,
        LocalDate datePosted,
        String url,
        String salaryRange,
        PersistenceOutcome outcome) {

    public NotificationPosting {
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}

