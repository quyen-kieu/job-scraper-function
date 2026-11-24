package com.jobscraper.domain.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One persisted daily observation of a job posting.
 *
 * <p>Persisted in the Cosmos DB {@code job-observations} container, partitioned by
 * {@code /companyId}. Unlike {@link NormalizedJobPosting}, which holds only the current
 * state, this record retains at most one observation per posting and UTC persistence date for
 * later month rebuilds. Multiple runs or redeliveries on the same date replace that observation.</p>
 *
 * <p><b>Note on {@code runId}:</b> the {@code job.postings.normalized} event does not carry
 * the originating scrape run ID. Consequently, {@code runId} here identifies the persistence
 * invocation that recorded this observation, not the upstream Kafka scrape command's run ID.
 * Wiring the true scrape {@code runId} through to persistence requires a wire-contract change.</p>
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
     * <p>Format: {@code {companyId}:{scrapeDate}:{externalJobId}}. Ensures the same posting is
     * stored once per company and date, even if Kafka redelivers the event. The persistence
     * handler supplies the current UTC date, which can differ from the original scrape date.</p>
     *
     * @param companyId the company ID
     * @param scrapeDate the observation date
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
