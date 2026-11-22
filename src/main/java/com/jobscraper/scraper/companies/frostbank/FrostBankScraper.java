package com.jobscraper.scraper.companies.frostbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FrostBankScraper implements CompanyScraper {
    private static final String SEARCH_URL = "https://careers.frostbank.com/us/en/c/technology-digital-jobs";
    private static final int PAGE_SIZE = 10;

    private final JobSearchHttpClient httpClient;
    private final RawScrapeStorage storage;
    private final ObjectMapper objectMapper;
    private final JobScraperProperties properties;
    private final FrostBankPageParser parser;

    public FrostBankScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                            ObjectMapper objectMapper, JobScraperProperties properties) {
        this.httpClient = httpClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.parser = new FrostBankPageParser(objectMapper);
    }

    @Override
    public String companyId() {
        return "frost-bank";
    }

    @Override
    public ScrapeResult scrape(ScrapeContext context) throws Exception {
        Instant startedAt = Instant.now();
        List<String> paths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, FrostBankPageParser.Job> jobs = new LinkedHashMap<>();
        Map<String, String> salaries = new HashMap<>();
        int pagesFetched = fetchSearchPages(context, paths, errors, jobs);
        int detailsFetched = fetchDetails(context, paths, errors, jobs, salaries, pagesFetched > 0);

        Instant completedAt = Instant.now();
        List<ScrapedJob> scrapedJobs = jobs.values().stream()
                .map(job -> new ScrapedJob(job.id(), job.title(), job.location(), job.postedDate(),
                        job.url().toString(), salaries.get(job.id())))
                .toList();
        try {
            RunSummary summary = new RunSummary(context.runId(), context.companyId(), startedAt, completedAt,
                    pagesFetched, detailsFetched, scrapedJobs, errors);
            paths.add(storage.writeRunSummary(context.companyId(), context.runId(),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary)));
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }
        return new ScrapeResult(context.runId(), context.companyId(), startedAt, completedAt,
                pagesFetched, detailsFetched, paths, errors, scrapedJobs);
    }

    private int fetchSearchPages(ScrapeContext context, List<String> paths, List<String> errors,
                                 Map<String, FrostBankPageParser.Job> jobs) throws InterruptedException {
        int fetched = 0;
        for (int page = 1; page <= properties.maxSearchPagesPerRun(); page++) {
            if (page > 1) {
                delay();
            }
            FrostBankPageParser.SearchPage search;
            try {
                URI uri = URI.create(SEARCH_URL + "?from=" + ((long) (page - 1) * PAGE_SIZE));
                search = parser.parseSearch(getBody(uri));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + page + " failed: " + e.getMessage());
                break;
            }
            try {
                paths.add(storage.writeSearchPage(context.companyId(), context.runId(), page, search.rawSearchJson()));
            } catch (Exception e) {
                errors.add("Search page " + page + " storage write failed: " + e.getMessage());
            }
            fetched++;
            search.jobs().forEach(job -> jobs.putIfAbsent(job.id(), job));
            if (search.jobs().isEmpty() && (long) (page - 1) * PAGE_SIZE < search.total()) {
                errors.add("Search page " + page + " was empty before reaching the reported total");
                break;
            }
            if ((long) page * PAGE_SIZE >= search.total()) {
                break;
            }
        }
        return fetched;
    }

    private int fetchDetails(ScrapeContext context, List<String> paths, List<String> errors,
                             Map<String, FrostBankPageParser.Job> jobs, Map<String, String> salaries,
                             boolean hasPreviousRequest) throws InterruptedException {
        int fetched = 0;
        int attempted = 0;
        for (FrostBankPageParser.Job job : jobs.values()) {
            if (attempted >= properties.maxDetailPagesPerRun()) {
                break;
            }
            if (hasPreviousRequest) {
                delay();
            }
            hasPreviousRequest = true;
            attempted++;
            String html;
            try {
                html = getBody(job.url());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Detail fetch failed for job " + job.id() + ": " + e.getMessage());
                continue;
            }
            fetched++;
            try {
                paths.add(storage.writeJobDetail(context.companyId(), context.runId(), job.id(), html));
            } catch (Exception e) {
                errors.add("Detail storage write failed for job " + job.id() + ": " + e.getMessage());
            }
            try {
                SalaryRangeExtractor.extract(parser.parseDescription(html, job.id()))
                        .ifPresent(salary -> salaries.put(job.id(), salary));
            } catch (Exception e) {
                errors.add("Detail parse failed for job " + job.id() + ": " + e.getMessage());
            }
        }
        return fetched;
    }

    private String getBody(URI uri) throws Exception {
        HttpResponse<String> response = httpClient.get(uri);
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void delay() throws InterruptedException {
        try {
            Thread.sleep(properties.requestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private record RunSummary(String runId, String companyId, Instant startedAt, Instant completedAt,
                              int searchPagesFetched, int detailPagesFetched, List<ScrapedJob> jobs,
                              List<String> errors) {
    }
}
