package com.jobscraper.infrastructure.cosmos;

import com.jobscraper.domain.job.JobObservation;
import java.util.List;

/**
 * Contract for persisting daily job observation documents.
 *
 * <p>Implementations must be idempotent: upserting a document with the same
 * {@link JobObservation#id()} (deterministic per company, scrape date, and external job ID)
 * must replace, not duplicate, the existing document.</p>
 */
public interface JobObservationRepository {

    /**
     * Upserts a job observation document.
     *
     * @param observation the observation to persist
     */
    void upsert(JobObservation observation);

    /**
     * Returns observations for a company within a month ({@code yyyy-MM}).
     *
     * @param companyId company identifier
     * @param month month in {@code yyyy-MM} format
     * @return matching observations (possibly empty)
     */
    List<JobObservation> findByCompanyAndMonth(String companyId, String month);
}
