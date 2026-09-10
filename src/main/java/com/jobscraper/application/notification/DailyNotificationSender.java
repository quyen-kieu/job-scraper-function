package com.jobscraper.application.notification;

/**
 * Contract for sending daily notification summaries.
 */
public interface DailyNotificationSender {

    void send(DailyNotificationRequestedEvent event) throws Exception;
}

