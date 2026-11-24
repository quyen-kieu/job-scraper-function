package com.jobscraper.scraper.api;

import com.jobscraper.domain.job.DescriptionFormat;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One job posting discovered by a company scraper, carrying the human-readable fields that
 * downstream consumers (normalization, daily email) need without re-reading raw responses.
 *
 * @param externalJobId the company site's stable job identifier
 * @param title the job title as displayed on the company site
 * @param location the location as displayed on the company site
 * @param datePosted the posting date, or {@code null} if it could not be parsed
 * @param url the absolute public URL of the job detail page, or {@code null} if unknown
 * @param salaryRange the salary range read from the job description (e.g. {@code $101,000 - $168,400}),
 *                    or {@code null} if the detail page was not fetched or lists no pay
 * @param descriptionPath reference to the source description handoff, or {@code null}
 * @param descriptionFormat format of the stored source; absent on legacy plain-text events
 */
public record ScrapedJob(
        String externalJobId,
        String title,
        String location,
        LocalDate datePosted,
        String url,
        String salaryRange,
        String descriptionPath,
        DescriptionFormat descriptionFormat) {

    public ScrapedJob(String externalJobId, String title, String location, LocalDate datePosted,
                      String url, String salaryRange) {
        this(externalJobId, title, location, datePosted, url, salaryRange, null, null);
    }

    public ScrapedJob(String externalJobId, String title, String location, LocalDate datePosted,
                      String url, String salaryRange, String descriptionPath) {
        this(externalJobId, title, location, datePosted, url, salaryRange, descriptionPath, null);
    }

    public ScrapedJob withDescriptionPath(String path) {
        return new ScrapedJob(externalJobId, title, location, datePosted, url, salaryRange, path, null);
    }

    public ScrapedJob withDescriptionReference(DescriptionReference reference) {
        return new ScrapedJob(externalJobId, title, location, datePosted, url, salaryRange,
                reference == null ? null : reference.path(), reference == null ? null : reference.format());
    }

    public ScrapedJob {
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(location, "location must not be null");

        if (externalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId must not be blank");
        }
    }
}
