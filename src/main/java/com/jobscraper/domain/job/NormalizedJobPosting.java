package com.jobscraper.domain.job;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Canonical, current-state representation of a single job posting.
 *
 * <p>Persisted in the Cosmos DB {@code job-postings} container, partitioned by
 * {@code /companyId}. One document exists per unique {@code companyId:externalJobId} pair;
 * Kafka redelivery must upsert this document rather than create duplicates.</p>
 */
public record NormalizedJobPosting(
        String id,
        String companyId,
        String externalJobId,
        String titleRaw,
        String titleNormalized,
        String jobLevel,
        String location,
        LocalDate datePosted,
        Instant firstSeenAt,
        Instant lastSeenAt,
        boolean isActive,
        String contentHash,
        int schemaVersion,
        String url,
        // Read old documents without writing the legacy field back to Cosmos.
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) String descriptionPath,
        @JsonInclude(JsonInclude.Include.NON_NULL) DescriptionFormat descriptionFormat,
        String descriptionHtml,
        String descriptionPlainText,
        @JsonInclude(JsonInclude.Include.NON_NULL) String scrapeRunId,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean persistedSynchronously,
        @JsonInclude(JsonInclude.Include.NON_NULL) String salaryRange) {

    public NormalizedJobPosting(String id, String companyId, String externalJobId, String titleRaw,
                                String titleNormalized, String jobLevel, String location, LocalDate datePosted,
                                Instant firstSeenAt, Instant lastSeenAt, boolean isActive, String contentHash,
                                int schemaVersion, String url, String description, String descriptionPath,
                                DescriptionFormat descriptionFormat, String descriptionHtml, String descriptionPlainText) {
        this(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel, location, datePosted,
                firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion, url, description,
                descriptionPath, descriptionFormat, descriptionHtml, descriptionPlainText, null, false, null);
    }

    public NormalizedJobPosting(String id, String companyId, String externalJobId, String titleRaw,
                                String titleNormalized, String jobLevel, String location, LocalDate datePosted,
                                Instant firstSeenAt, Instant lastSeenAt, boolean isActive, String contentHash,
                                int schemaVersion, String url, String description, String descriptionPath) {
        this(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel, location, datePosted,
                firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion, url, description,
                descriptionPath, null, null, null);
    }

    public NormalizedJobPosting(String id, String companyId, String externalJobId, String titleRaw,
                                String titleNormalized, String jobLevel, String location, LocalDate datePosted,
                                Instant firstSeenAt, Instant lastSeenAt, boolean isActive, String contentHash,
                                int schemaVersion, String url) {
        this(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel, location, datePosted,
                firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion, url, null, null, null, null, null);
    }

    /**
     * Creates a posting without a public URL. Also matches the wire/document shape written
     * before {@code url} was added; for those, {@code url} deserializes as {@code null}.
     */
    public NormalizedJobPosting(
            String id,
            String companyId,
            String externalJobId,
            String titleRaw,
            String titleNormalized,
            String jobLevel,
            String location,
            LocalDate datePosted,
            Instant firstSeenAt,
            Instant lastSeenAt,
            boolean isActive,
            String contentHash,
            int schemaVersion) {
        this(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel, location, datePosted,
                firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion, null, null, null, null, null, null);
    }

    public NormalizedJobPosting withDescription(String text) {
        return withDescriptions(null, text);
    }

    public NormalizedJobPosting withDescriptions(String html, String text) {
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, datePosted, firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion,
                url, null, null, null, html, text, scrapeRunId, persistedSynchronously, salaryRange);
    }

    public NormalizedJobPosting withRun(String runId, boolean persisted) {
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, datePosted, firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion,
                url, description, descriptionPath, descriptionFormat, descriptionHtml, descriptionPlainText,
                runId, persisted, salaryRange);
    }

    public NormalizedJobPosting withActive(boolean active) {
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, datePosted, firstSeenAt, lastSeenAt, active, contentHash, schemaVersion,
                url, description, descriptionPath, descriptionFormat, descriptionHtml, descriptionPlainText,
                scrapeRunId, persistedSynchronously, salaryRange);
    }

    public NormalizedJobPosting withFirstSeenAt(Instant firstSeen) {
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, datePosted, firstSeen, lastSeenAt, isActive, contentHash, schemaVersion,
                url, description, descriptionPath, descriptionFormat, descriptionHtml, descriptionPlainText,
                scrapeRunId, persistedSynchronously, salaryRange);
    }

    public NormalizedJobPosting withSalaryRange(String salary) {
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, datePosted, firstSeenAt, lastSeenAt, isActive, contentHash, schemaVersion,
                url, description, descriptionPath, descriptionFormat, descriptionHtml, descriptionPlainText,
                scrapeRunId, persistedSynchronously, salary);
    }

    public NormalizedJobPosting withPreservedDetails(NormalizedJobPosting previous) {
        String text = previous.descriptionPlainText() != null ? previous.descriptionPlainText() : previous.description();
        String hash = titleRaw.equals(previous.titleRaw()) && location.equals(previous.location())
                ? previous.contentHash() : contentHash;
        return new NormalizedJobPosting(id, companyId, externalJobId, titleRaw, titleNormalized, jobLevel,
                location, previous.datePosted() != null ? previous.datePosted() : datePosted,
                firstSeenAt, lastSeenAt, isActive, hash, schemaVersion, url, null, null, null,
                previous.descriptionHtml(), text, scrapeRunId, persistedSynchronously, previous.salaryRange());
    }

    public NormalizedJobPosting {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(titleRaw, "titleRaw must not be null");
        Objects.requireNonNull(titleNormalized, "titleNormalized must not be null");
        Objects.requireNonNull(jobLevel, "jobLevel must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
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
     * Builds the deterministic document ID for a job posting.
     *
     * <p>Format: {@code {companyId}:{externalJobId}}. Used to upsert instead of duplicating
     * documents on Kafka redelivery.</p>
     *
     * @param companyId the company ID
     * @param externalJobId the external job ID from the company site
     * @return the deterministic document ID
     */
    public static String buildId(String companyId, String externalJobId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        return companyId + ":" + externalJobId;
    }
}
