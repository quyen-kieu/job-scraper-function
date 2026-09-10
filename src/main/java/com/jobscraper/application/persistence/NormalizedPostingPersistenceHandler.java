package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.RunCompletionAggregator;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Application service that deserializes a {@code job.postings.normalized} message
 * and performs idempotent persistence into Cosmos DB.
 *
 * <p>This is the sole persistence boundary for the platform: it upserts both the
 * current-state {@link NormalizedJobPosting} document and a dated {@link JobObservation}
 * record, per the README's job-deduplication guarantee. Persistence occurs before
 * this method returns, so any failure here propagates to the caller and blocks the
 * Kafka checkpoint (see {@link com.jobscraper.function.PersistenceConsumerFunction}).</p>
 *
 * <p><b>Stage 6:</b> also classifies each upsert as {@link PersistenceOutcome#NEW},
 * {@link PersistenceOutcome#UPDATED}, or {@link PersistenceOutcome#UNCHANGED} (by comparing
 * against any prior document's {@code contentHash}) and reports it to
 * {@link RunCompletionAggregator}. Once every posting from a company's scrape has been
 * reported, the aggregator returns a completed daily summary, which is published to
 * {@code notifications.daily} via {@link DailyNotificationPublisher}.</p>
 */
@Service
public class NormalizedPostingPersistenceHandler {

    private final ObjectMapper objectMapper;
    private final JobPostingRepository postingRepository;
    private final JobObservationRepository observationRepository;
    private final RunCompletionAggregator runCompletionAggregator;
    private final DailyNotificationPublisher dailyNotificationPublisher;
    private final Clock clock;

    @Autowired
    public NormalizedPostingPersistenceHandler(
            ObjectMapper objectMapper,
            JobPostingRepository postingRepository,
            JobObservationRepository observationRepository,
            RunCompletionAggregator runCompletionAggregator,
            DailyNotificationPublisher dailyNotificationPublisher) {
        this(objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, Clock.systemUTC());
    }

    NormalizedPostingPersistenceHandler(
            ObjectMapper objectMapper,
            JobPostingRepository postingRepository,
            JobObservationRepository observationRepository,
            RunCompletionAggregator runCompletionAggregator,
            DailyNotificationPublisher dailyNotificationPublisher,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.postingRepository = postingRepository;
        this.observationRepository = observationRepository;
        this.runCompletionAggregator = runCompletionAggregator;
        this.dailyNotificationPublisher = dailyNotificationPublisher;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.postings.normalized} event.
     *
     * <p>Steps:
     * <ol>
     *   <li>Deserialize JSON into {@link NormalizedJobPosting}</li>
     *   <li>Look up any prior document to classify a {@link PersistenceOutcome}</li>
     *   <li>Upsert the posting into the {@code job-postings} container</li>
     *   <li>Build a {@link JobObservation} keyed by the current scrape date and upsert it
     *       into the {@code job-observations} container</li>
     *   <li>Report the outcome to {@link RunCompletionAggregator}; publish a daily notification
     *       if this was the last expected posting for the company's scrape</li>
     * </ol>
     *
     * @param json the raw JSON message
     * @return the persisted posting
     * @throws Exception any deserialization or repository failure (triggers redelivery)
     */
    public NormalizedJobPosting handle(String json) throws Exception {
        NormalizedJobPosting posting = objectMapper.readValue(json, NormalizedJobPosting.class);

        PersistenceOutcome outcome = postingRepository.findById(posting.id(), posting.companyId())
                .map(existing -> existing.contentHash().equals(posting.contentHash())
                        ? PersistenceOutcome.UNCHANGED
                        : PersistenceOutcome.UPDATED)
                .orElse(PersistenceOutcome.NEW);

        postingRepository.upsert(posting);

        LocalDate scrapeDate = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Instant observedAt = Instant.now(clock);

        // The job.postings.normalized wire schema does not carry the originating scrape
        // runId (see JobObservation's Javadoc). A fresh UUID identifies this persistence
        // invocation instead.
        JobObservation observation = new JobObservation(
                JobObservation.buildId(posting.companyId(), scrapeDate, posting.externalJobId()),
                posting.companyId(),
                posting.externalJobId(),
                UUID.randomUUID().toString(),
                observedAt,
                scrapeDate,
                posting.datePosted(),
                posting.titleNormalized(),
                posting.jobLevel(),
                posting.contentHash(),
                posting.schemaVersion()
        );

        observationRepository.upsert(observation);

        runCompletionAggregator
                .recordPersistedPosting(posting.companyId(), outcome)
                .ifPresent(dailyNotificationPublisher::publish);

        return posting;
    }
}
