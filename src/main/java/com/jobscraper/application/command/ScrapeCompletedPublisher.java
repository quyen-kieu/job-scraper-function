package com.jobscraper.application.command;

/**
 * Contract for publishing {@code job.scrape.completed} events to Kafka.
 */
public interface ScrapeCompletedPublisher {

    /**
     * Publishes a scrapecompleted event.
     *
     * @param event the event to publish
     */
    void publish(ScrapeCompletedEvent event);
}

