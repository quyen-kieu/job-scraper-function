package com.jobscraper.application.persistence;

/**
 * Classifies the result of upserting a {@link com.jobscraper.domain.job.NormalizedJobPosting}
 * document, used to build the Stage 6 daily notification summary counts.
 */
public enum PersistenceOutcome {

    /** No prior document existed for this {@code companyId:externalJobId}. */
    NEW,

    /** A prior document existed and its {@code contentHash} differed from the new value. */
    UPDATED,

    /** A prior document existed and its {@code contentHash} matched the new value. */
    UNCHANGED
}

