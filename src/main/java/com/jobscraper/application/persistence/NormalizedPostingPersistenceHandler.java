package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobDescriptionText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;

/**
 * Application service that deserializes a {@code job.postings.normalized} message
 * and performs idempotent persistence into Cosmos DB.
 *
 * <p>Upserts both the current-state {@link NormalizedJobPosting} document and a dated
 * {@link JobObservation} record using deterministic IDs. The normalizer calls this service
 * synchronously before reconciliation. The persistence consumer also calls it for legacy
 * events; events marked as already persisted are not written twice.</p>
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
     *   <li>Build a {@link JobObservation} keyed by the UTC scrape date and upsert it
     *       into the {@code job-observations} container</li>
     * </ol>
     *
     * @param json the raw JSON message
     * @return the persisted posting
     * @throws Exception any deserialization or repository failure (triggers redelivery)
     */
    public NormalizedJobPosting handle(String json) throws Exception {
        NormalizedJobPosting posting = objectMapper.readValue(json, NormalizedJobPosting.class);
        if (posting.persistedSynchronously()) {
            return posting;
        }
        return persist(posting).orElse(posting);
    }

    /**
     * Persists a posting and observation before the caller can acknowledge its run.
     * Empty means a newer run already superseded the posting.
     */
    public Optional<NormalizedJobPosting> persist(NormalizedJobPosting posting) throws Exception {
        boolean preserveDescription = posting.descriptionPath() == null && posting.description() == null
                && posting.descriptionPlainText() == null && posting.descriptionHtml() == null;

        if (posting.descriptionPath() != null) {
            String description = rawStorage.readDescription(
                    posting.companyId(), posting.externalJobId(), posting.descriptionPath());
            if (description == null || description.isBlank()) {
                throw new IllegalStateException("Stored description is empty for " + posting.id());
            }
            String html = posting.descriptionFormat() == DescriptionFormat.HTML ? description : null;
            String text = posting.descriptionFormat() != null
                    ? JobDescriptionText.fromHtml(description) : description;
            if (text.isBlank()) {
                throw new IllegalStateException("Stored description has no readable text for " + posting.id());
            }
            posting = posting.withDescriptions(html, text);
        } else if (posting.description() == null && posting.descriptionPlainText() == null
                && posting.descriptionHtml() == null) {
            var existing = postingRepository.findById(posting.id(), posting.companyId());
            String html = existing.map(NormalizedJobPosting::descriptionHtml).orElse(null);
            String text = existing.map(previous -> previous.descriptionPlainText() != null
                    ? previous.descriptionPlainText() : previous.description()).orElse(null);
            posting = posting.withDescriptions(html, text);
        } else {
            String text = posting.descriptionPlainText() != null
                    ? posting.descriptionPlainText() : posting.description();
            posting = posting.withDescriptions(posting.descriptionHtml(), text);
        }
        if (!postingRepository.upsertIfCurrent(posting, preserveDescription)) {
            return Optional.empty();
        }

        LocalDate scrapeDate = posting.scrapeRunId() == null
                ? LocalDate.now(clock.withZone(ZoneOffset.UTC))
                : posting.lastSeenAt().atZone(ZoneOffset.UTC).toLocalDate();
        Instant observedAt = Instant.now(clock);

        JobObservation observation = new JobObservation(
                JobObservation.buildId(posting.companyId(), scrapeDate, posting.externalJobId()),
                posting.companyId(),
                posting.externalJobId(),
                posting.scrapeRunId() != null ? posting.scrapeRunId() : UUID.randomUUID().toString(),
                observedAt,
                scrapeDate,
                posting.datePosted(),
                posting.titleNormalized(),
                posting.jobLevel(),
                posting.contentHash(),
                posting.schemaVersion()
        );

        observationRepository.upsert(observation);

        return Optional.of(posting);
    }
}
