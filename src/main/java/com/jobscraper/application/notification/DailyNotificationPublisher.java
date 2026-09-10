package com.jobscraper.application.notification;

/**
 * Contract for publishing {@code notifications.daily} events to Kafka.
 */
public interface DailyNotificationPublisher {

    /**
     * Publishes a daily notification request event.
     *
     * @param event the event to publish
     */
    void publish(DailyNotificationRequestedEvent event);
}

