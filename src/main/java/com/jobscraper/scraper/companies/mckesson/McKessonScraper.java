package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CompanyScraper} implementation for the McKesson company.
 *
 * <p>Fetches real search-results pages and job-detail pages from
 * {@code careers.mckesson.com}, verified live on 2026-08-23 (see
 * {@link McKessonSearchResultsParser} and {@link McKessonJobDetailParser} for the exact
 * endpoint and selectors). Raw responses are written via {@link RawScrapeStorage}, and a
 * structured {@link McKessonRunSummary} is written as the run-summary document.</p>
 *
 * <p>Per-run limits ({@link JobScraperProperties#maxSearchPagesPerRun()},
 * {@link JobScraperProperties#maxDetailPagesPerRun()}) cap the amount of live traffic sent
 * to the real site, and {@link JobScraperProperties#requestDelay()} adds a small pause
 * between requests. Full retry/backoff/circuit-breaker/DLT handling remains deferred, as
 * documented in the README's Stage 3 status.</p>
 */
@Component
public class McKessonScraper implements CompanyScraper {

    private static final String BASE_URL = "https://careers.mckesson.com";
    private static final String SEARCH_PATH = "/en/search-jobs/results";
    private static final String SEARCH_QUERY_TEMPLATE =
            "ActiveFacetID=0&CurrentPage=%d&RecordsPerPage=15&Distance=50&RadiusUnitType=0"
                    + "&Keywords=&Location=&ShowRadius=False&IsPagination=True&FacetTerm=&FacetType=0"
                    + "&SearchResultsModuleName=Search+Results&SearchFilterModuleName=Search+Filter"
                    + "&SortCriteria=0&SortDirection=0&SearchType=5&PostalCode=";

    private final JobSearchHttpClient httpClient;
    private final RawScrapeStorage storage;
    private final ObjectMapper objectMapper;
    private final JobScraperProperties properties;

    public McKessonScraper(
            JobSearchHttpClient httpClient,
            RawScrapeStorage storage,
            ObjectMapper objectMapper,
            JobScraperProperties properties) {
        this.httpClient = httpClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String companyId() {
        return "mckesson";
    }

    @Override
    public ScrapeResult scrape(ScrapeContext context) throws Exception {
        Instant startedAt = Instant.now();
        List<String> blobPaths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<McKessonJobSummary> allJobs = new LinkedHashSet<>();

        int searchPagesFetched = fetchSearchPages(context, blobPaths, errors, allJobs);
        int detailPagesFetched = fetchDetailPages(context, blobPaths, errors, allJobs);

        Instant completedAt = Instant.now();

        writeRunSummary(context, startedAt, completedAt, searchPagesFetched, detailPagesFetched,
                allJobs, errors, blobPaths);

        return new ScrapeResult(
                context.runId(),
                context.companyId(),
                startedAt,
                completedAt,
                searchPagesFetched,
                detailPagesFetched,
                blobPaths,
                errors
        );
    }

    private int fetchSearchPages(
            ScrapeContext context,
            List<String> blobPaths,
            List<String> errors,
            Set<McKessonJobSummary> allJobs) {
        int searchPagesFetched = 0;
        int page = 1;
        int totalPages = 1;
        int pageCap = properties.maxSearchPagesPerRun();

        while (page <= totalPages && searchPagesFetched < pageCap) {
            HttpResponse<String> response;
            try {
                response = httpClient.get(buildSearchUri(page));
            } catch (Exception e) {
                errors.add("Search page " + page + " fetch failed: " + e.getMessage());
                break;
            }

            McKessonSearchResponse envelope;
            try {
                envelope = objectMapper.readValue(response.body(), McKessonSearchResponse.class);
            } catch (Exception e) {
                errors.add("Search page " + page + " parse failed: " + e.getMessage());
                break;
            }

            try {
                blobPaths.add(storage.writeSearchPage(context.companyId(), context.runId(), page, response.body()));
            } catch (Exception e) {
                errors.add("Search page " + page + " storage write failed: " + e.getMessage());
            }
            searchPagesFetched++;

            McKessonSearchPage parsedPage = McKessonSearchResultsParser.parse(envelope.results());
            allJobs.addAll(parsedPage.jobs());
            if (parsedPage.totalPages() > 0) {
                totalPages = parsedPage.totalPages();
            }

            page++;
            sleepPolitely(errors);
        }

        return searchPagesFetched;
    }

    private int fetchDetailPages(
            ScrapeContext context,
            List<String> blobPaths,
            List<String> errors,
            Set<McKessonJobSummary> allJobs) {
        int detailPagesFetched = 0;
        int detailCap = properties.maxDetailPagesPerRun();

        for (McKessonJobSummary job : allJobs) {
            if (detailPagesFetched >= detailCap) {
                break;
            }

            try {
                HttpResponse<String> response = httpClient.get(URI.create(BASE_URL + job.detailPath()));
                blobPaths.add(storage.writeJobDetail(
                        context.companyId(), context.runId(), job.externalJobId(), response.body()));
                detailPagesFetched++;
            } catch (Exception e) {
                errors.add("Detail page fetch failed for job " + job.externalJobId() + ": " + e.getMessage());
            }

            sleepPolitely(errors);
        }

        return detailPagesFetched;
    }

    private void writeRunSummary(
            ScrapeContext context,
            Instant startedAt,
            Instant completedAt,
            int searchPagesFetched,
            int detailPagesFetched,
            Set<McKessonJobSummary> allJobs,
            List<String> errors,
            List<String> blobPaths) {
        try {
            McKessonRunSummary summary = new McKessonRunSummary(
                    context.runId(),
                    context.companyId(),
                    startedAt,
                    completedAt,
                    searchPagesFetched,
                    detailPagesFetched,
                    List.copyOf(allJobs),
                    errors
            );
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
            blobPaths.add(storage.writeRunSummary(context.companyId(), context.runId(), json));
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }
    }

    private URI buildSearchUri(int page) {
        return URI.create(BASE_URL + SEARCH_PATH + "?" + String.format(SEARCH_QUERY_TEMPLATE, page));
    }

    private void sleepPolitely(List<String> errors) {
        try {
            Thread.sleep(properties.requestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Request delay interrupted: " + e.getMessage());
        }
    }
}


