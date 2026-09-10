package com.jobscraper.scraper.companies.mckesson;

import java.util.Objects;

/**
 * One job entry as it appears in a McKesson search-results page, before the detail page
 * is fetched.
 *
 * @param externalJobId the stable {@code data-job-id} value
 * @param titleRaw the job title as displayed in the search results list
 * @param location the location as displayed in the search results list
 * @param datePostedRaw the raw {@code MM/dd/yyyy} date-posted string as displayed
 * @param detailPath the site-relative path to the job detail page (e.g. {@code /en/job/...})
 */
public record McKessonJobSummary(
        String externalJobId,
        String titleRaw,
        String location,
        String datePostedRaw,
        String detailPath) {

    public McKessonJobSummary {
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(titleRaw, "titleRaw must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(datePostedRaw, "datePostedRaw must not be null");
        Objects.requireNonNull(detailPath, "detailPath must not be null");
    }
}

