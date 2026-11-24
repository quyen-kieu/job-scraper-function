package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
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
 * record using deterministic IDs. Persistence occurs before
 * this method returns, so any failure here propagates to the caller and blocks the
 * Kafka checkpoint (see {@link com.jobscraper.function.PersistenceConsumerFunction}).</p>
 *
 * <p>New/changed/unchanged classification and the daily notification are handled upstream by
 * {@link com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler}, which
 * sees the whole company run in one invocation.</p>
 */
@Service
public class NormalizedPostingPersistenceHandler {

    private final ObjectMapper objectMapper;
    private final JobPostingRepository postingRepository;
    private final JobObservationRepository observationRepository;
    private final RawScrapeStorage rawStorage;
    private final Clock clock;

    @Autowired
    public NormalizedPostingPersistenceHandler(
            ObjectMapper objectMapper,
            JobPostingRepository postingRepository,
            JobObservationRepository observationRepository,
            RawScrapeStorage rawStorage) {
        this(objectMapper, postingRepository, observationRepository, rawStorage, Clock.systemUTC());
    }

    NormalizedPostingPersistenceHandler(
            ObjectMapper objectMapper,
            JobPostingRepository postingRepository,
            JobObservationRepository observationRepository,
            RawScrapeStorage rawStorage,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.postingRepository = postingRepository;
        this.observationRepository = observationRepository;
        this.rawStorage = rawStorage;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.postings.normalized} event.
     *
     * <p>Steps:
     * <ol>
     *   <li>Deserialize JSON into {@link NormalizedJobPosting}</li>
     *   <li>Upsert the posting into the {@code job-postings} container</li>
     *   <li>Build a {@link JobObservation} keyed by the current UTC persistence date and upsert it
     *       into the {@code job-observations} container</li>
     * </ol>
     *
     * @param json the raw JSON message
     * @return the persisted posting
     * @throws Exception any deserialization or repository failure (triggers redelivery)
     */
    public NormalizedJobPosting handle(String json) throws Exception {
        NormalizedJobPosting posting = objectMapper.readValue(json, NormalizedJobPosting.class);

        if (posting.descriptionPath() != null) {
            String description = rawStorage.readDescription(
                    posting.companyId(), posting.externalJobId(), posting.descriptionPath());
            if (description == null || description.isBlank()) {
                throw new IllegalStateException("Stored description is empty for " + posting.id());
            }
            posting = posting.withDescription(description);
        } else if (posting.description() == null) {
            String description = postingRepository.findById(posting.id(), posting.companyId())
                    .map(NormalizedJobPosting::description).orElse(null);
            posting = posting.withDescription(description);
        }
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

        return posting;
    }
}
