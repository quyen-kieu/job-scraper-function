package com.jobscraper.scraper.companies.oracle;

import java.util.List;

/**
 * Parsed Oracle Recruiting Cloud search-results page.
 *
 * @param totalJobsCount total number of requisitions matching the search across all pages
 * @param requisitions the requisitions on this page
 */
public record OracleSearchPage(int totalJobsCount, List<OracleRequisitionSummary> requisitions) {

    public OracleSearchPage {
        requisitions = requisitions == null ? List.of() : List.copyOf(requisitions);
    }
}

