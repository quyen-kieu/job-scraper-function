package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;

import java.util.List;
import java.util.Optional;

/**
 * Aggregates one company's scrape-and-persist run into a {@link DailyNotificationRequestedEvent},
 * so a daily summary email can be sent once every discovered posting for that company's run has
 * been persisted.
 *
 * <p><b>Per-company scope, not per-run:</b> aggregation is keyed by {@code companyId} rather than
 * {@code runId}, because the registered {@code job.postings.normalized} JSON Schema contract
 * (additionalProperties: false) does not carry the originating scrape {@code runId} — the same
 * constraint already documented on {@link com.jobscraper.domain.job.JobObservation}. This means
 * one daily notification is published per completed company scrape rather than batched across
 * every company in a shared cron run. This mirrors the architecture's existing "one message per
 * company" philosophy (see the README's "Parallel scraping and failure isolation" section) and
 * avoids fragile cross-company synchronization. Batching multiple companies into a single combined
 * email is a natural future enhancement once a durable run-registry exists (Stage 7/8 concern).</p>
 */
public interface RunCompletionAggregator {

    /**
     * Registers that a company's scrape has completed and normalization has published
     * {@code expectedPostings} events for it. Must be called once per company scrape, before any
     * {@link #recordPersistedPosting(String, PersistenceOutcome)} calls for that company arrive.
     *
     * @param runId the scrape run ID (used only as the resulting event's {@code runId})
     * @param companyId the company ID
     * @param expectedPostings the number of {@code job.postings.normalized} events published for
     *                         this company's scrape (may be zero)
     * @param scrapeErrors errors recorded during scraping (from {@link com.jobscraper.application.command.ScrapeCompletedEvent#errors()})
     * @return a completed summary event immediately if {@code expectedPostings} is zero (nothing
     *         to wait for), otherwise empty
     */
    Optional<DailyNotificationRequestedEvent> registerExpectedCompany(
            String runId, String companyId, int expectedPostings, List<String> scrapeErrors);

    /**
     * Records that one posting for {@code companyId} has been persisted with the given outcome.
     *
     * @param companyId the company ID
     * @param outcome whether the posting was new, updated, or unchanged
     * @return a completed summary event once every expected posting for this company has been
     *         recorded, otherwise empty
     */
    Optional<DailyNotificationRequestedEvent> recordPersistedPosting(String companyId, PersistenceOutcome outcome);
}

