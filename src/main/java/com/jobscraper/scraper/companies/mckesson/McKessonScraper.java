package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.JobDescriptionText;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes McKesson's Workday career site using its configured search facets.
 */
@Component
public class McKessonScraper implements CompanyScraper {

    private static final String BASE_URL = "https://mckesson.wd3.myworkdayjobs.com";
    private static final String API_PATH = "/wday/cxs/mckesson/External_Careers";
    private static final String PUBLIC_PATH = "/en-US/External_Careers";
    private static final URI SEARCH_URI = URI.create(BASE_URL + API_PATH + "/jobs");
    private static final int PAGE_SIZE = 20;
    private static final Pattern RELATIVE_DATE = Pattern.compile("^Posted (\\d+) Days? Ago$");
    private static final Pattern FACET_ID = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final JobSearchHttpClient httpClient;
    private final RawScrapeStorage storage;
    private final ObjectMapper objectMapper;
    private final JobScraperProperties properties;

    public McKessonScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                           ObjectMapper objectMapper, JobScraperProperties properties) {
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
        LocalDate today = startedAt.atZone(ZoneOffset.UTC).toLocalDate();
        List<String> paths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        int pagesFetched = fetchSearchPages(context, today, candidates, paths, errors);
        int detailsFetched = fetchDetails(context, candidates, paths, errors);
        Instant completedAt = Instant.now();
        List<ScrapedJob> jobs = candidates.values().stream().map(Candidate::job).toList();

        try {
            McKessonRunSummary summary = new McKessonRunSummary(context.runId(), context.companyId(),
                    startedAt, completedAt, pagesFetched, detailsFetched, jobs, errors);
            paths.add(storage.writeRunSummary(context.companyId(), context.runId(),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            errors.add("Run summary write failed: " + e.getMessage());
        }

        return new ScrapeResult(context.runId(), context.companyId(), startedAt, completedAt,
                pagesFetched, detailsFetched, paths, errors, jobs);
    }

    private int fetchSearchPages(ScrapeContext context, LocalDate today,
                                 Map<String, Candidate> candidates, List<String> paths,
                                 List<String> errors) throws InterruptedException {
        int fetched = 0;
        int total = Integer.MAX_VALUE;
        for (int page = 0; page < properties.maxSearchPagesPerRun(); page++) {
            int offset = page * PAGE_SIZE;
            try {
                if (page > 0) {
                    pause();
                }
                String body = requireSuccess(httpClient.postJson(SEARCH_URI, searchRequest(offset)));
                JsonNode root = objectMapper.readTree(body);
                JsonNode postings = root.path("jobPostings");
                if (!postings.isArray() || (page == 0 && !root.path("total").isIntegralNumber())) {
                    throw new IllegalArgumentException("Invalid Workday search response");
                }
                if (page == 0) {
                    total = root.path("total").intValue();
                }
                if (postings.isEmpty() && offset < total) {
                    throw new IllegalArgumentException("Workday returned an empty page before the reported total");
                }
                List<Candidate> parsed = new ArrayList<>();
                for (JsonNode posting : postings) {
                    parsed.add(parsePosting(posting, today));
                }
                try {
                    paths.add(storage.writeSearchPage(context.companyId(), context.runId(), page + 1, body));
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    errors.add("Search page " + (page + 1) + " storage write failed: " + e.getMessage());
                }
                fetched++;
                parsed.forEach(candidate -> candidates.putIfAbsent(candidate.job().externalJobId(), candidate));
                if (postings.isEmpty() || offset + postings.size() >= total) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Search page " + (page + 1) + " failed: " + e.getMessage());
                break;
            }
        }
        return fetched;
    }

    private String searchRequest(int offset) throws Exception {
        Map<String, List<String>> facets = Map.of(
                "Location_Region_State_Province", List.of(requireFacet(properties.getMckessonStateFacetId())),
                "jobFamilyGroup", List.of(requireFacet(properties.getMckessonJobFamilyFacetId())),
                "Location_Country", List.of(requireFacet(properties.getMckessonCountryFacetId())));
        return objectMapper.writeValueAsString(Map.of(
                "appliedFacets", facets, "limit", PAGE_SIZE, "offset", offset, "searchText", ""));
    }

    private static String requireFacet(String id) {
        if (id == null || !FACET_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("McKesson Workday facet IDs must be 32 hexadecimal characters");
        }
        return id;
    }

    private static Candidate parsePosting(JsonNode posting, LocalDate today) {
        String title = posting.path("title").asText("");
        String path = posting.path("externalPath").asText("");
        JsonNode bullets = posting.path("bulletFields");
        String id = bullets.isArray() && !bullets.isEmpty() ? bullets.get(0).asText("") : "";
        if (title.isBlank() || id.isBlank() || !path.startsWith("/job/") || path.contains("..")
                || path.contains("?") || path.contains("#") || path.contains("\\")) {
            throw new IllegalArgumentException("Workday search posting is missing a valid title, job ID or path");
        }
        String location = posting.path("locationsText").asText("Unknown");
        ScrapedJob job = new ScrapedJob(id, title, location, relativeDate(posting.path("postedOn").asText(""), today),
                BASE_URL + PUBLIC_PATH + path, null);
        return new Candidate(path, job);
    }

    private int fetchDetails(ScrapeContext context, Map<String, Candidate> candidates,
                             List<String> paths, List<String> errors) throws InterruptedException {
        int fetched = 0;
        for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
            Candidate candidate = entry.getValue();
            try {
                pause();
                String body = requireSuccess(httpClient.get(URI.create(BASE_URL + API_PATH + candidate.path())));
                JsonNode info = objectMapper.readTree(body).path("jobPostingInfo");
                if (!info.isObject()) {
                    throw new IllegalArgumentException("Invalid Workday detail response");
                }
                fetched++;
                try {
                    paths.add(storage.writeJobDetail(context.companyId(), context.runId(), entry.getKey(), body));
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    errors.add("Detail storage write failed for job " + entry.getKey() + ": " + e.getMessage());
                }
                ScrapedJob job = candidate.job();
                LocalDate date = absoluteDate(info.path("startDate").asText(""));
                String salary = SalaryRangeExtractor.extract(info.path("jobDescription").asText("")).orElse(null);
                String description = JobDescriptionText.fromHtml(info.path("jobDescription").asText(""));
                String descriptionPath = null;
                if (description.isBlank()) {
                    errors.add("Missing description for job " + entry.getKey());
                } else {
                    try {
                        descriptionPath = java.util.Objects.requireNonNull(storage.writeDescription(
                                context.companyId(), context.runId(), entry.getKey(), description),
                                "Description storage returned no path");
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        errors.add("Description storage failed for job " + entry.getKey() + ": " + e.getMessage());
                    }
                }
                entry.setValue(new Candidate(candidate.path(), new ScrapedJob(job.externalJobId(), job.title(),
                        job.location(), date != null ? date : job.datePosted(), job.url(), salary, descriptionPath)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                errors.add("Detail fetch failed for job " + entry.getKey() + ": " + e.getMessage());
            }
        }
        return fetched;
    }

    private static LocalDate relativeDate(String value, LocalDate today) {
        if ("Posted Today".equals(value)) {
            return today;
        }
        if ("Posted Yesterday".equals(value)) {
            return today.minusDays(1);
        }
        Matcher matcher = RELATIVE_DATE.matcher(value);
        if (matcher.matches()) {
            try {
                return today.minusDays(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException | java.time.DateTimeException e) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate absoluteDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String requireSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void pause() throws InterruptedException {
        try {
            Thread.sleep(properties.requestDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private record Candidate(String path, ScrapedJob job) {
    }
}
