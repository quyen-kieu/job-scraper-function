package com.jobscraper.scraper.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One job posting discovered by a company scraper, carrying the human-readable fields that
 * downstream consumers (normalization, daily email) need without re-reading raw blobs.
 *
 * @param externalJobId the company site's stable job identifier
 * @param title the job title as displayed on the company site
 * @param location the location as displayed on the company site
 * @param datePosted the posting date, or {@code null} if it could not be parsed
 * @param url the absolute public URL of the job detail page, or {@code null} if unknown
 */
public record ScrapedJob(
        String externalJobId,
        String title,
        String location,
        LocalDate datePosted,
        String url) {

    public ScrapedJob {
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(location, "location must not be null");

        if (externalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId must not be blank");
        }
    }
}

