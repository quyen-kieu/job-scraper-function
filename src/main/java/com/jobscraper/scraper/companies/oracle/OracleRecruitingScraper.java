package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;

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
 * <p>Pages through the keyword search newest-first, then fetches up to
 * {@link JobScraperProperties#maxDetailPagesPerRun()} job details to read the salary range from the
 * description. Raw responses are written via {@link RawScrapeStorage}. Per-run caps and
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

        int searchPagesFetched = fetchSearchPages(context, blobPaths, errors, requisitionsById);
        int detailPagesFetched = fetchDetails(context, blobPaths, errors, requisitionsById, salaryRangesById);

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
                        .map(requisition -> toScrapedJob(requisition, salaryRangesById.get(requisition.id())))
                        .toList());
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
            Map<String, OracleRequisitionSummary> requisitionsById) {
        int searchPagesFetched = 0;
        int offset = 0;
        int pageCap = properties.maxSearchPagesPerRun();

        while (searchPagesFetched < pageCap) {
            int pageNumber = searchPagesFetched + 1;
            String body;
            try {
                body = getBody(site.searchUri(offset, PAGE_SIZE));
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
            } catch (Exception e) {
                errors.add("Search page " + pageNumber + " storage write failed: " + e.getMessage());
            }
            searchPagesFetched++;

            page.requisitions().forEach(requisition -> requisitionsById.putIfAbsent(requisition.id(), requisition));
            offset += page.requisitions().size();
            if (page.requisitions().isEmpty() || offset >= page.totalJobsCount()) {
                break;
            }
            sleepPolitely(errors);
        }
        return searchPagesFetched;
    }

    private int fetchDetails(
            ScrapeContext context,
            List<String> blobPaths,
            List<String> errors,
            Map<String, OracleRequisitionSummary> requisitionsById,
            Map<String, String> salaryRangesById) {
        int detailPagesFetched = 0;
        int detailCap = properties.maxDetailPagesPerRun();

        for (OracleRequisitionSummary requisition : requisitionsById.values()) {
            if (detailPagesFetched >= detailCap) {
                break;
            }
            sleepPolitely(errors);

            try {
                String body = getBody(site.detailUri(requisition.id()));
                blobPaths.add(storage.writeJobDetail(context.companyId(), context.runId(), requisition.id(), body));
                detailPagesFetched++;
                String description = OracleRecruitingResponseParser.parseDescriptionText(objectMapper, body);
                SalaryRangeExtractor.extractFromText(description)
                        .ifPresent(range -> salaryRangesById.put(requisition.id(), range));
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
            List<String> blobPaths) {
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
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }
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

