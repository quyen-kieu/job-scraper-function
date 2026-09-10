package com.jobscraper.domain.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One observation of a job posting during a single daily scrape run.
 *
 * <p>Persisted in the Cosmos DB {@code job-observations} container, partitioned by
 * {@code /companyId}. Unlike {@link NormalizedJobPosting}, which holds only the current
 * state, this record preserves a full history of sightings for later month rebuilds.</p>
 *
 * <p><b>Note on {@code runId}:</b> the {@code job.postings.normalized} Kafka message schema
 * (per the README's Confluent Schema Registry contract) does not carry the originating
 * scrape {@code runId} in its {@code payload}, and its JSON Schema forbids additional
 * properties. Consequently, {@code runId} here identifies the persistence invocation
 * that recorded this observation, not the upstream Kafka scrape command's run ID. Wiring the
 * true scrape {@code runId} through to persistence would require a wire-schema change and is
 * out of scope for this stage.</p>
 */
public record JobObservation(
        String id,
        String companyId,
        String externalJobId,
        String runId,
        Instant observedAt,
        LocalDate scrapeDate,
        LocalDate datePosted,
        String titleNormalized,
        String jobLevel,
        String contentHash,
        int schemaVersion) {

    public JobObservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(scrapeDate, "scrapeDate must not be null");
        Objects.requireNonNull(titleNormalized, "titleNormalized must not be null");
        Objects.requireNonNull(jobLevel, "jobLevel must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }

        if (externalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId must not be blank");
        }
    }

    /**
     * Builds the deterministic document ID for a job observation.
     *
     * <p>Format: {@code {companyId}:{scrapeDate}:{externalJobId}}, per the README's
     * job-deduplication guarantee. Ensures the same posting is recorded only once per
     * company and scrape date, even if Kafka redelivers the event.</p>
     *
     * @param companyId the company ID
     * @param scrapeDate the date of the scrape run
     * @param externalJobId the external job ID from the company site
     * @return the deterministic document ID
     */
    public static String buildId(String companyId, LocalDate scrapeDate, String externalJobId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(scrapeDate, "scrapeDate must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        return companyId + ":" + scrapeDate + ":" + externalJobId;
    }
}


