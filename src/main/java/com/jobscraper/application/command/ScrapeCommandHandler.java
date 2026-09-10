package com.jobscraper.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.registry.CompanyScraperRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Application service that deserializes a {@code job.scrape.commands} message
 * and delegates to the appropriate company scraper.
 *
 * <p>This service is stateless and thread-safe. It is used by
 * {@link com.jobscraper.function.ScraperConsumerFunction} to process
 * Kafka messages without business logic in the function class.</p>
 */
@Service
public class ScrapeCommandHandler {

    private final ObjectMapper objectMapper;
    private final CompanyScraperRegistry registry;
    private final ScrapeCompletedPublisher scrapeCompletedPublisher;
    private final Clock clock;

    @Autowired
    public ScrapeCommandHandler(
            ObjectMapper objectMapper,
            CompanyScraperRegistry registry,
            ScrapeCompletedPublisher scrapeCompletedPublisher) {
        this(objectMapper, registry, scrapeCompletedPublisher, Clock.systemUTC());
    }

    ScrapeCommandHandler(
            ObjectMapper objectMapper,
            CompanyScraperRegistry registry,
            ScrapeCompletedPublisher scrapeCompletedPublisher,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.scrapeCompletedPublisher = scrapeCompletedPublisher;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.scrape.commands} event.
     *
     * <p>Steps:
     * <ol>
     *   <li>Deserialize JSON into {@link ScrapeCompanyCommand}</li>
     *   <li>Look up the matching {@link com.jobscraper.scraper.api.CompanyScraper} via registry</li>
     *   <li>Throw {@link UnknownCompanyScraperException} if not found</li>
     *   <li>Invoke the scraper with a {@link ScrapeContext}</li>
     *   <li>Publish a {@link ScrapeCompletedEvent} built from the {@link ScrapeResult}
     *       to {@code job.scrape.completed} via {@link ScrapeCompletedPublisher}</li>
     *   <li>Return the {@link ScrapeResult}</li>
     * </ol>
     *
     * <p>The completed-event publish happens before this method returns, so the Azure Kafka
     * trigger only checkpoints the {@code job.scrape.commands} offset after the downstream
     * {@code job.scrape.completed} event has been successfully published. If publishing fails,
     * the exception propagates and the offset is not committed, allowing redelivery.</p>
     *
     * <p>Any exception (deserialization, missing scraper, scraper failure, or publish failure)
     * is propagated and will cause the Azure Kafka trigger to not commit the offset, allowing
     * redelivery.</p>
     *
     * @param json the raw JSON message
     * @return the scrape result
     * @throws UnknownCompanyScraperException if no scraper is registered for the company
     * @throws Exception any deserialization, scraper, or publish exception
     */
    public ScrapeResult handle(String json) throws Exception {
        ScrapeCompanyCommand command = objectMapper.readValue(json, ScrapeCompanyCommand.class);

        ScrapeContext context = new ScrapeContext(
                command.runId(),
                command.companyId(),
                command.requestedAt()
        );

        ScrapeResult result = registry.find(command.companyId())
                .orElseThrow(() -> new UnknownCompanyScraperException(
                        "No scraper registered for company: " + command.companyId()
                ))
                .scrape(context);

        scrapeCompletedPublisher.publish(toScrapeCompletedEvent(result));

        return result;
    }

    private static ScrapeCompletedEvent toScrapeCompletedEvent(ScrapeResult result) {
        return new ScrapeCompletedEvent(
                result.runId(),
                result.companyId(),
                result.startedAt(),
                result.completedAt(),
                result.searchPagesFetched(),
                result.detailPagesFetched(),
                result.blobPaths(),
                result.errors()
        );
    }

    /**
     * Exception thrown when no scraper is found for a requested company.
     */
    public static class UnknownCompanyScraperException extends RuntimeException {
        public UnknownCompanyScraperException(String message) {
            super(message);
        }
    }
}
