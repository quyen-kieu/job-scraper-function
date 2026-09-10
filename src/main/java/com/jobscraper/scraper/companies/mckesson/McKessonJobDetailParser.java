package com.jobscraper.scraper.companies.mckesson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;

/**
 * Parses a McKesson job detail page into its title and location.
 *
 * <p>Verified against a live {@code careers.mckesson.com/en/job/...} page on 2026-08-23:</p>
 * <pre>
 * &lt;section id="ajd-header" data-org-id="733" data-job-id="99563388640"&gt;
 *   &lt;h2 class="job-description__job-title"&gt;Senior Director, Agentic Service Experience&lt;/h2&gt;
 *   &lt;span class="job-description__job-location"&gt;Fort Worth, Texas&lt;/span&gt;
 * &lt;/section&gt;
 * </pre>
 */
public final class McKessonJobDetailParser {

    private static final String HEADER_SELECTOR = "section#ajd-header";
    private static final String TITLE_SELECTOR = "h2.job-description__job-title";
    private static final String LOCATION_SELECTOR = "span.job-description__job-location";

    private McKessonJobDetailParser() {
        // Non-instantiable utility class
    }

    /**
     * Parses the job detail page HTML.
     *
     * @param html the raw detail page HTML
     * @return the parsed title/location, or empty if the expected header section is missing
     */
    public static Optional<McKessonJobDetail> parse(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(html);
        Element header = doc.selectFirst(HEADER_SELECTOR);
        if (header == null) {
            return Optional.empty();
        }

        Element titleEl = header.selectFirst(TITLE_SELECTOR);
        Element locationEl = header.selectFirst(LOCATION_SELECTOR);

        String title = titleEl != null ? titleEl.text() : "";
        String location = locationEl != null ? locationEl.text() : "";

        if (title.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new McKessonJobDetail(title, location));
    }

    /**
     * Parsed job detail page fields.
     *
     * @param titleRaw the job title
     * @param location the job location
     */
    public record McKessonJobDetail(String titleRaw, String location) {
    }
}

