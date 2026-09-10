package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.job.NormalizedJobPosting;

import java.util.Optional;

/**
 * Contract for persisting the current-state normalized job posting document.
 *
 * <p>Implementations must be idempotent: upserting a document with the same
 * {@link NormalizedJobPosting#id()} must replace, not duplicate, the existing document.</p>
 */
public interface JobPostingRepository {

    /**
     * Upserts a normalized job posting document.
     *
     * @param posting the posting to persist
     */
    void upsert(NormalizedJobPosting posting);

    /**
     * Looks up the current-state document for a posting, used by
     * {@link com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler} to
     * classify a persistence outcome (new, updated, or unchanged) for the Stage 6 daily
     * notification summary.
     *
     * @param id the deterministic document ID ({@link NormalizedJobPosting#buildId(String, String)})
     * @param companyId the partition key value
     * @return the existing document, or empty if none exists yet
     */
    Optional<NormalizedJobPosting> findById(String id, String companyId);
}

