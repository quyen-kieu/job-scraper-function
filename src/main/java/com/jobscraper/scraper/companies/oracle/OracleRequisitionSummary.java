package com.jobscraper.scraper.companies.oracle;

import java.util.Objects;

/**
 * One requisition entry from an Oracle Recruiting Cloud search-results page.
 *
 * @param id the requisition {@code Id} (stable external job ID)
 * @param title the job title
 * @param location the primary location as displayed, e.g. {@code Irving, TX, United States}
 * @param postedDateRaw the posting date as returned by the API ({@code yyyy-MM-dd}), or empty
 */
public record OracleRequisitionSummary(String id, String title, String location, String postedDateRaw) {

    public OracleRequisitionSummary {
        Objects.requireNonNull(id, "id must not be null");
        title = Objects.requireNonNullElse(title, "");
        location = Objects.requireNonNullElse(location, "");
        postedDateRaw = Objects.requireNonNullElse(postedDateRaw, "");
    }
}

