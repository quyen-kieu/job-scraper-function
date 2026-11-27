package com.jobscraper.domain.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One persisted daily observation of a job posting.
 *
 * <p>Persisted in the Cosmos DB {@code job-observations} container, partitioned by
 * {@code /companyId}. Unlike {@link NormalizedJobPosting}, which holds only the current
 * state, this record retains at most one observation per posting and UTC scrape date for
 * later month rebuilds. Multiple runs or redeliveries on the same date replace that observation.</p>
 *
 * <p>For legacy events without a scrape run ID, the persistence date and a generated
 * invocation ID are used instead.</p>
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
     * stored once per company and date, even if Kafka redelivers the event.</p>
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
