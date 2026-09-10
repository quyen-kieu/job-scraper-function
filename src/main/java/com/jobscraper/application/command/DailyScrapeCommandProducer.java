package com.jobscraper.application.command;

import com.jobscraper.function.JobScraperProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes {@link ScrapeCompanyCommand} messages to {@code job.scrape.commands}.
 *
 * <p>This service is trigger-agnostic: it is used both by the daily timer
 * ({@code DailyScrapeCommandProducerFunction}) and by the on-demand HTTP trigger
 * ({@code OnDemandScrapeCommandProducerFunction}). Both callers publish the same
 * {@link ScrapeCompanyCommand} shape through the same {@link ScrapeCommandPublisher},
 * so every downstream Kafka consumer behaves identically regardless of trigger source.</p>
 */
@Service
public class DailyScrapeCommandProducer {

    private final JobScraperProperties properties;
    private final ScrapeCommandPublisher publisher;
    private final Clock clock;

    @Autowired
    public DailyScrapeCommandProducer(JobScraperProperties properties, ScrapeCommandPublisher publisher) {
        this(properties, publisher, Clock.systemUTC());
    }

    DailyScrapeCommandProducer(JobScraperProperties properties, ScrapeCommandPublisher publisher, Clock clock) {
        this.properties = properties;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Publishes one {@link ScrapeCompanyCommand} per enabled company, sharing a single run ID.
     *
     * <p>Used by the daily timer trigger and by the on-demand HTTP trigger when no specific
     * {@code companyId} is requested.</p>
     *
     * @return the number of commands published
     */
    public int publishDailyCommands() {
        List<JobScraperProperties.CompanyProperties> companies = properties.enabledCompanies();
        if (companies.isEmpty()) {
            return 0;
        }

        String runId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now(clock);

        for (JobScraperProperties.CompanyProperties company : companies) {
            publisher.publish(new ScrapeCompanyCommand(runId, company.id(), requestedAt));
        }

        return companies.size();
    }

    /**
     * Publishes a single {@link ScrapeCompanyCommand} for one on-demand company, with its own
     * dedicated run ID.
     *
     * <p>Used by the on-demand HTTP trigger when a specific {@code companyId} is requested.
     * The company must be present in configuration and enabled; otherwise nothing is published.</p>
     *
     * @param companyId the company to scrape on demand
     * @return the generated run ID if the command was published, or {@link Optional#empty()}
     *         if the company is unknown or disabled
     */
    public Optional<String> publishCommand(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Optional.empty();
        }

        boolean isEnabled = properties.enabledCompanies().stream()
                .anyMatch(company -> company.id().equals(companyId));
        if (!isEnabled) {
            return Optional.empty();
        }

        String runId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now(clock);
        publisher.publish(new ScrapeCompanyCommand(runId, companyId, requestedAt));
        return Optional.of(runId);
    }
}
