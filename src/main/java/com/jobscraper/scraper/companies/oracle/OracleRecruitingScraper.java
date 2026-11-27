package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.DescriptionReference;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.JobDescriptionText;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;
import com.jobscraper.scraper.api.SearchScanTracker;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable {@link CompanyScraper} for any company whose careers site runs on Oracle Recruiting
 * Cloud (Oracle HCM Candidate Experience). Each company is registered as its own bean with its own
 * {@link OracleRecruitingSite}, e.g. {@code GmFinancialScraperConfiguration}.
 *
 * <p>Pages through the keyword search newest-first, then fetches each discovered job's details.
 * Raw responses are written via {@link RawScrapeStorage}. Search-page caps and
 * {@link JobScraperProperties#requestDelay()} keep traffic to the live site polite, matching
 * {@code McKessonScraper}.</p>
 */
public class OracleRecruitingScraper implements CompanyScraper {

    /** Page size used by the Candidate Experience UI itself. */
    static final int PAGE_SIZE = 25;
    private static final int HTTP_ERROR_THRESHOLD = 400;

    private final OracleRecruitingSite site;
    private final JobSearchHttpClient httpClient;
    private final RawScrapeStorage storage;
    private final ObjectMapper objectMapper;
    private final JobScraperProperties properties;

    public OracleRecruitingScraper(
            OracleRecruitingSite site,
            JobSearchHttpClient httpClient,
            RawScrapeStorage storage,
            ObjectMapper objectMapper,
            JobScraperProperties properties) {
        this.site = Objects.requireNonNull(site, "site must not be null");
        this.httpClient = httpClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String companyId() {
        return site.companyId();
    }

    @Override
    public ScrapeResult scrape(ScrapeContext context) throws Exception {
        Instant startedAt = Instant.now();
        List<String> blobPaths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, OracleRequisitionSummary> requisitionsById = new LinkedHashMap<>();
        Map<String, String> salaryRangesById = new HashMap<>();
        Map<String, DescriptionReference> descriptionPathsById = new HashMap<>();

        SearchScanTracker tracker = new SearchScanTracker();
        int searchPagesFetched = fetchSearchPages(context, blobPaths, errors, requisitionsById, tracker);
        int detailPagesFetched = fetchDetails(context, blobPaths, errors, requisitionsById,
                salaryRangesById, descriptionPathsById);

        Instant completedAt = Instant.now();
        writeRunSummary(context, startedAt, completedAt, searchPagesFetched, detailPagesFetched,
                List.copyOf(requisitionsById.values()), errors, blobPaths);

        return new ScrapeResult(
                context.runId(),
                context.companyId(),
                startedAt,
                completedAt,
                searchPagesFetched,
                detailPagesFetched,
                blobPaths,
                errors,
                requisitionsById.values().stream()
                        .map(requisition -> toScrapedJob(requisition, salaryRangesById.get(requisition.id()))
                                .withDescriptionReference(descriptionPathsById.get(requisition.id())))
                        .toList(),
                tracker.isComplete(requisitionsById.size()), context.fullDetailRefresh());
    }

    /**
     * Maps a search-results requisition to the company-agnostic {@link ScrapedJob}. The API returns
     * ISO {@code yyyy-MM-dd} dates; unparseable values become {@code null} rather than failing the run.
     */
    ScrapedJob toScrapedJob(OracleRequisitionSummary requisition, String salaryRange) {
        LocalDate datePosted;
        try {
            datePosted = requisition.postedDateRaw().isBlank() ? null : LocalDate.parse(requisition.postedDateRaw());
        } catch (DateTimeParseException e) {
            datePosted = null;
        }
        return new ScrapedJob(requisition.id(), requisition.title(), requisition.location(), datePosted,
                site.publicJobUrl(requisition.id()), salaryRange);
    }

    private int fetchSearchPages(
            ScrapeContext context,
            List<String> blobPaths,
            List<String> errors,
            Map<String, OracleRequisitionSummary> requisitionsById,
            SearchScanTracker tracker) throws InterruptedException {
        int searchPagesFetched = 0;
        int offset = 0;
        while (properties.canFetchSearchPage(searchPagesFetched)) {
            int pageNumber = searchPagesFetched + 1;
            String body;
            try {
                body = getBody(site.searchUri(offset, PAGE_SIZE));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + pageNumber + " fetch failed: " + e.getMessage());
                break;
            }

            OracleSearchPage page;
            try {
                page = OracleRecruitingResponseParser.parseSearchPage(objectMapper, body);
            } catch (Exception e) {
                errors.add("Search page " + pageNumber + " parse failed: " + e.getMessage());
                break;
            }

            try {
                blobPaths.add(storage.writeSearchPage(context.companyId(), context.runId(), pageNumber, body));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + pageNumber + " storage write failed: " + e.getMessage());
            }
            searchPagesFetched++;

            page.requisitions().forEach(requisition -> requisitionsById.putIfAbsent(requisition.id(), requisition));
            offset += page.requisitions().size();
            if (page.requisitions().isEmpty() && offset < page.totalJobsCount()) {
                errors.add("Search page " + pageNumber + " was empty before reaching the reported total");
                break;
            }
            tracker.recordPage(page.totalJobsCount(),
                    page.requisitions().isEmpty() || offset >= page.totalJobsCount());
            if (page.requisitions().isEmpty() || offset >= page.totalJobsCount()) {
                break;
            }
            sleepPolitely();
        }
        return searchPagesFetched;
    }

    private int fetchDetails(
            ScrapeContext context,
            List<String> blobPaths,
            List<String> errors,
            Map<String, OracleRequisitionSummary> requisitionsById,
            Map<String, String> salaryRangesById,
            Map<String, DescriptionReference> descriptionPathsById) throws InterruptedException {
        int detailPagesFetched = 0;

        for (OracleRequisitionSummary requisition : requisitionsById.values()) {
            if (!context.shouldFetchDetail(toScrapedJob(requisition, null), true)) {
                continue;
            }
            sleepPolitely();

            try {
                String body = getBody(site.detailUri(requisition.id()));
                detailPagesFetched++;
                try {
                    blobPaths.add(storage.writeJobDetail(context.companyId(), context.runId(), requisition.id(), body));
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    errors.add("Detail storage failed for job " + requisition.id() + ": " + e.getMessage());
                }
                String description = OracleRecruitingResponseParser.parseDescriptionSource(objectMapper, body);
                String text = JobDescriptionText.fromHtml(description);
                SalaryRangeExtractor.extractFromText(text)
                        .ifPresent(range -> salaryRangesById.put(requisition.id(), range));
                if (text.isBlank()) {
                    throw new IllegalArgumentException("Empty job description");
                }
                String path = Objects.requireNonNull(storage.writeDescription(
                        context.companyId(), context.runId(), requisition.id(), description),
                        "Description storage returned no path");
                descriptionPathsById.put(requisition.id(),
                        new DescriptionReference(path, JobDescriptionText.sourceFormat(description)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Detail fetch failed for job " + requisition.id() + ": " + e.getMessage());
            }
        }
        return detailPagesFetched;
    }

    private String getBody(URI uri) throws Exception {
        HttpResponse<String> response = httpClient.get(uri);
        if (response.statusCode() >= HTTP_ERROR_THRESHOLD) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void writeRunSummary(
            ScrapeContext context,
            Instant startedAt,
            Instant completedAt,
            int searchPagesFetched,
            int detailPagesFetched,
            List<OracleRequisitionSummary> requisitions,
            List<String> errors,
            List<String> blobPaths) throws InterruptedException {
        try {
            OracleRunSummary summary = new OracleRunSummary(
                    context.runId(),
                    context.companyId(),
                    site.keyword(),
                    startedAt,
                    completedAt,
                    searchPagesFetched,
                    detailPagesFetched,
                    requisitions,
                    errors);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
            blobPaths.add(storage.writeRunSummary(context.companyId(), context.runId(), json));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }
    }

    private void sleepPolitely() throws InterruptedException {
        try {
            Thread.sleep(properties.requestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
