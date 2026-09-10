package com.jobscraper.scraper.companies.mckesson;

import java.util.List;
import java.util.Objects;

/**
 * Parsed result of one McKesson search-results HTML fragment: pagination metadata plus the
 * job summaries listed on that page.
 */
public record McKessonSearchPage(
        int totalJobResults,
        int totalPages,
        int currentPage,
        int recordsPerPage,
        List<McKessonJobSummary> jobs) {

    public McKessonSearchPage {
        Objects.requireNonNull(jobs, "jobs must not be null");
        jobs = List.copyOf(jobs);
    }
}

