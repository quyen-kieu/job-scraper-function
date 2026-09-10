package com.jobscraper.application.notification;

/**
 * Guards against sending a duplicate daily notification email for the same {@code runId}, per
 * the README's Stage 6 requirement to "prevent duplicate emails for the same run ID."
 *
 * <p>The realistic risk this guards against: Kafka redelivers a {@code notifications.daily}
 * message when a prior consumer process is interrupted after {@link DailyNotificationSender#send}
 * succeeds (a real HTTP call to Resend) but before the Azure Kafka trigger commits the offset.
 * Unlike Cosmos upserts elsewhere in this pipeline, sending an email is not naturally idempotent,
 * so an explicit guard is required.</p>
 */
public interface SentNotificationGuard {

    /**
     * @param runId the daily notification run ID
     * @return {@code true} if a notification for this run ID has already been sent
     */
    boolean alreadySent(String runId);

    /**
     * Records that a notification for this run ID has been sent successfully.
     *
     * @param runId the daily notification run ID
     */
    void markSent(String runId);
}

