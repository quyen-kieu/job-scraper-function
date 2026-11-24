package com.jobscraper.scraper.companies.firstcitizens;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FirstCitizensScraper implements CompanyScraper {

    private static final String BASE_URL = "https://firstcitizens.jibeapply.com";
    private static final int PAGE_SIZE = 10;

    private final JobSearchHttpClient httpClient;
    private final RawScrapeStorage storage;
    private final ObjectMapper objectMapper;
    private final JobScraperProperties properties;

    public FirstCitizensScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                                ObjectMapper objectMapper, JobScraperProperties properties) {
        this.httpClient = httpClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String companyId() {
        return "first-citizens";
    }

    @Override
    public ScrapeResult scrape(ScrapeContext context) throws Exception {
        Instant startedAt = Instant.now();
        List<String> paths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, ScrapedJob> jobs = new LinkedHashMap<>();
        int pagesFetched = 0;

        for (int page = 1; page <= properties.maxSearchPagesPerRun(); page++) {
            if (page > 1) {
                Thread.sleep(properties.requestDelay().toMillis());
            }

            String body;
            SearchPage searchPage;
            try {
                HttpResponse<String> response = httpClient.get(searchUri(page));
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                body = response.body();
                searchPage = parseSearchPage(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + page + " failed: " + e.getMessage());
                break;
            }

            try {
                paths.add(storage.writeSearchPage(context.companyId(), context.runId(), page, body));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + page + " storage write failed: " + e.getMessage());
            }
            pagesFetched++;
            for (ParsedJob parsed : searchPage.jobs()) {
                ScrapedJob job = parsed.job();
                if (jobs.containsKey(job.externalJobId())) {
                    continue;
                }
                if (JobDescriptionText.fromHtml(parsed.description()).isBlank()) {
                    errors.add("Missing description for job " + job.externalJobId());
                } else {
                    try {
                        String path = java.util.Objects.requireNonNull(storage.writeDescription(
                                context.companyId(), context.runId(), job.externalJobId(), parsed.description()),
                                "Description storage returned no path");
                        job = job.withDescriptionReference(
                                new DescriptionReference(path, JobDescriptionText.sourceFormat(parsed.description())));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception e) {
                        errors.add("Description storage failed for job " + job.externalJobId() + ": " + e.getMessage());
                    }
                }
                jobs.put(job.externalJobId(), job);
            }
            if (searchPage.jobs().isEmpty() && (long) (page - 1) * PAGE_SIZE < searchPage.totalCount()) {
                errors.add("Search page " + page + " was empty before reaching the reported total");
                break;
            }
            if ((long) page * PAGE_SIZE >= searchPage.totalCount()) {
                break;
            }
        }

        Instant completedAt = Instant.now();
        List<ScrapedJob> scrapedJobs = List.copyOf(jobs.values());
        try {
            RunSummary summary = new RunSummary(context.runId(), context.companyId(), startedAt, completedAt,
                    pagesFetched, 0, scrapedJobs, errors);
            paths.add(storage.writeRunSummary(context.companyId(), context.runId(),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }
        return new ScrapeResult(context.runId(), context.companyId(), startedAt, completedAt,
                pagesFetched, 0, paths, errors, scrapedJobs);
    }

    private static URI searchUri(int page) {
        return URI.create(BASE_URL + "/api/jobs?page=" + page
                + "&categories=Information%20Technology&sortBy=posted_date&descending=true");
    }

    private SearchPage parseSearchPage(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.path("jobs").isArray() || !root.path("totalCount").isIntegralNumber()
                || !root.path("totalCount").canConvertToInt() || root.path("totalCount").intValue() < 0) {
            throw new IllegalArgumentException("Invalid jobs API response");
        }
        List<ParsedJob> jobs = new ArrayList<>();
        for (JsonNode entry : root.path("jobs")) {
            JsonNode data = entry.path("data");
            String id = requiredText(data, "req_id");
            String title = requiredText(data, "title");
            String slug = requiredText(data, "slug");
            String location = data.path("full_location").asText("");
            if (location.isBlank()) {
                location = data.path("short_location").asText("");
            }
            String posted = data.path("posted_date").asText("");
            LocalDate date = null;
            try {
                if (posted.length() >= 10) {
                    date = LocalDate.parse(posted.substring(0, 10));
                }
            } catch (DateTimeParseException ignored) {
                // Consistent with other scrapers: an unparseable date remains unknown.
            }
            String url = BASE_URL + "/jobs/" + URLEncoder.encode(slug, StandardCharsets.UTF_8).replace("+", "%20")
                    + "?lang=en-us";
            String descriptionHtml = data.path("description").asText("");
            String salary = SalaryRangeExtractor.extract(descriptionHtml).orElse(null);
            jobs.add(new ParsedJob(new ScrapedJob(id, title, location, date, url, salary),
                    descriptionHtml));
        }
        return new SearchPage(root.path("totalCount").intValue(), jobs);
    }

    private static String requiredText(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing job " + field);
        }
        return value.textValue();
    }

    private record ParsedJob(ScrapedJob job, String description) {
    }

    private record SearchPage(int totalCount, List<ParsedJob> jobs) {
    }

    private record RunSummary(String runId, String companyId, Instant startedAt, Instant completedAt,
                              int searchPagesFetched, int detailPagesFetched, List<ScrapedJob> jobs,
                              List<String> errors) {
    }
}
