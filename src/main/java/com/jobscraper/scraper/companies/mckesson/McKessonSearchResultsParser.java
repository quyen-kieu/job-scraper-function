package com.jobscraper.scraper.companies.mckesson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the HTML fragment inside {@link McKessonSearchResponse#results()} into structured
 * pagination metadata and job summaries.
 *
 * <p>Verified against the live {@code careers.mckesson.com} search-results markup on
 * 2026-08-23. Selector reference:</p>
 * <pre>
 * &lt;section id="search-results" data-total-job-results="574" data-total-pages="39"
 *          data-current-page="1" data-records-per-page="15" ...&gt;
 *   &lt;ul&gt;
 *     &lt;li&gt;
 *       &lt;a class="search-results__job-title-link"
 *          href="/en/job/{slug}/{orgId}/{jobId}" data-job-id="{jobId}"&gt;{Title}&lt;/a&gt;
 *       &lt;span class="search-results__job-location"&gt;{Location}&lt;/span&gt;
 *       &lt;span class="search-results__job-date-posted"&gt;{MM/dd/yyyy}&lt;/span&gt;
 *     &lt;/li&gt;
 *   &lt;/ul&gt;
 * &lt;/section&gt;
 * </pre>
 */
public final class McKessonSearchResultsParser {

    private static final String RESULTS_SECTION_SELECTOR = "section#search-results";
    private static final String JOB_ROW_SELECTOR = "li";
    private static final String JOB_LINK_SELECTOR = "a.search-results__job-title-link";
    private static final String JOB_LOCATION_SELECTOR = "span.search-results__job-location";
    private static final String JOB_DATE_SELECTOR = "span.search-results__job-date-posted";

    private McKessonSearchResultsParser() {
        // Non-instantiable utility class
    }

    /**
     * Parses one search-results HTML fragment.
     *
     * @param resultsHtml the HTML fragment from {@link McKessonSearchResponse#results()}
     * @return parsed pagination metadata and job summaries; pagination fields default to
     *         {@code 0} and the job list is empty when the fragment is empty or malformed
     *         (e.g. no more results on the requested page)
     */
    public static McKessonSearchPage parse(String resultsHtml) {
        if (resultsHtml == null || resultsHtml.isBlank()) {
            return new McKessonSearchPage(0, 0, 0, 0, List.of());
        }

        Document doc = Jsoup.parse(resultsHtml);
        Element section = doc.selectFirst(RESULTS_SECTION_SELECTOR);
        if (section == null) {
            return new McKessonSearchPage(0, 0, 0, 0, List.of());
        }

        int totalJobResults = parseIntAttr(section, "data-total-job-results");
        int totalPages = parseIntAttr(section, "data-total-pages");
        int currentPage = parseIntAttr(section, "data-current-page");
        int recordsPerPage = parseIntAttr(section, "data-records-per-page");

        List<McKessonJobSummary> jobs = new ArrayList<>();
        Elements rows = section.select(JOB_ROW_SELECTOR);
        for (Element row : rows) {
            Element link = row.selectFirst(JOB_LINK_SELECTOR);
            if (link == null) {
                continue;
            }

            String jobId = link.attr("data-job-id");
            if (jobId.isBlank()) {
                continue;
            }

            String titleRaw = link.text();
            String detailPath = link.attr("href");

            Element locationEl = row.selectFirst(JOB_LOCATION_SELECTOR);
            String location = locationEl != null ? locationEl.text() : "";

            Element dateEl = row.selectFirst(JOB_DATE_SELECTOR);
            String datePostedRaw = dateEl != null ? dateEl.text() : "";

            jobs.add(new McKessonJobSummary(jobId, titleRaw, location, datePostedRaw, detailPath));
        }

        return new McKessonSearchPage(totalJobResults, totalPages, currentPage, recordsPerPage, jobs);
    }

    private static int parseIntAttr(Element element, String attrName) {
        String value = element.attr(attrName);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

