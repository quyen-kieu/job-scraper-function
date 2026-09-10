package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable representation of the McKesson search AJAX response envelope.
 *
 * <p>Verified against the live {@code https://careers.mckesson.com/en/search-jobs/results}
 * endpoint on 2026-08-23: the endpoint returns a small JSON envelope where {@link #results()}
 * is itself an HTML fragment string (not nested JSON). The pagination metadata
 * (e.g. {@code data-total-job-results}, {@code data-total-pages}, {@code data-current-page})
 * lives as HTML attributes on the {@code <section id="search-results">} element inside that
 * HTML fragment, and must be parsed out with Jsoup via {@link McKessonSearchResultsParser},
 * not by Jackson.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McKessonSearchResponse(
        String filters,
        String results,
        boolean hasJobs,
        boolean hasContent) {
}



