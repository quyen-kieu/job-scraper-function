package com.jobscraper.scraper.companies.firstcitizens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;
import com.jobscraper.scraper.registry.CompanyScraperRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstCitizensScraperTest {
    private static final String COMPANY_ID = "first-citizens";

    @Mock
    private JobSearchHttpClient httpClient;
    @Mock
    private RawScrapeStorage storage;

    private JobScraperProperties properties;
    private FirstCitizensScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        scraper = new FirstCitizensScraper(httpClient, storage, new ObjectMapper().findAndRegisterModules(), properties);
        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(invocation -> "search-" + invocation.getArgument(2));
        lenient().when(storage.writeDescription(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> "description-" + invocation.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString())).thenReturn("summary");
    }

    @Test
    void scrapesFilteredPagesDeduplicatesAndExtractsFields() throws Exception {
        String page1 = fixture("search-results-page1.json");
        String page2 = fixture("search-results-page2.json");
        HttpResponse<String> first = response(200, page1);
        HttpResponse<String> second = response(200, page2);
        when(httpClient.get(uri(1))).thenReturn(first);
        when(httpClient.get(uri(2))).thenReturn(second);

        ScrapeResult result = scraper.scrape(context());

        assertThat(scraper.companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(2);
        assertThat(result.detailPagesFetched()).isZero();
        assertThat(result.blobPaths()).containsExactly("search-1", "search-2", "summary");
        assertThat(result.jobs()).extracting(ScrapedJob::externalJobId).containsExactly("36571", "34381", "36358");
        assertThat(result.jobs().get(0)).isEqualTo(new ScrapedJob("36571",
                "Enterprise Resilience Analyst (Remote)", "Raleigh, North Carolina; North Carolina, United States",
                LocalDate.of(2026, 11, 19), "https://firstcitizens.jibeapply.com/jobs/36571?lang=en-us",
                "$95,000 and $125,000", "description-36571", DescriptionFormat.HTML));
        verify(storage).writeDescription(eq(COMPANY_ID), eq("run-1"), eq("36571"),
                org.mockito.ArgumentMatchers.argThat(text -> text.contains("$95,000") && text.contains("<p>")));
        assertThat(result.jobs().get(1).location()).isEqualTo("Raleigh, North Carolina");
        assertThat(result.jobs().get(1).salaryRange()).isNull();
        assertThat(result.jobs().get(2).salaryRange()).isEqualTo("$110,000 - $150,000");
        verify(storage).writeSearchPage(COMPANY_ID, "run-1", 1, page1);
        verify(storage).writeSearchPage(COMPANY_ID, "run-1", 2, page2);
        verify(storage).writeRunSummary(eq(COMPANY_ID), eq("run-1"), anyString());
        verify(httpClient, never()).get(uri(3));
    }

    @Test
    void respectsPageCap() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        HttpResponse<String> first = response(200, fixture("search-results-page1.json"));
        when(httpClient.get(uri(1))).thenReturn(first);
        ScrapeResult capped = scraper.scrape(context());
        assertThat(capped.searchPagesFetched()).isEqualTo(1);
        assertThat(capped.jobs()).hasSize(2);
        verify(httpClient, never()).get(uri(2));
    }

    @Test
    void unlimitedSearchFetchesBeyondThreePagesUntilReportedTotal() throws Exception {
        properties.setMaxSearchPagesPerRun(0);
        for (int page = 1; page <= 4; page++) {
            String body = "{\"totalCount\":31,\"jobs\":[{\"data\":{\"req_id\":\"" + page
                    + "\",\"slug\":\"" + page + "\",\"title\":\"Engineer\",\"description\":\"Description\"}}]}";
            HttpResponse<String> search = response(200, body);
            when(httpClient.get(uri(page))).thenReturn(search);
        }

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(4);
        assertThat(result.jobs()).extracting(ScrapedJob::externalJobId).containsExactly("1", "2", "3", "4");
        verify(httpClient, never()).get(uri(5));
    }

    @Test
    void storesPlainTextWhenSearchDescriptionHasNoHtml() throws Exception {
        HttpResponse<String> page = response(200, """
                {"totalCount":1,"jobs":[{"data":{"req_id":"123","slug":"123",
                "title":"Engineer","description":"Build systems"}}]}
                """);
        when(httpClient.get(uri(1))).thenReturn(page);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs().get(0).descriptionFormat()).isEqualTo(DescriptionFormat.PLAIN_TEXT);
        verify(storage).writeDescription(COMPANY_ID, "run-1", "123", "Build systems");
    }

    @Test
    void storesAllDescriptionsBeyondTenWithoutPuttingTextInCompletionMessage() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String text = "Detailed requirements ".repeat(5000);
        HttpResponse<String> first = response(200, generatedPage(mapper, 0, 10, text));
        HttpResponse<String> second = response(200, generatedPage(mapper, 10, 12, text));
        when(httpClient.get(uri(1))).thenReturn(first);
        when(httpClient.get(uri(2))).thenReturn(second);
        when(storage.writeDescription(eq(COMPANY_ID), eq("run-1"), eq("1000"), anyString()))
                .thenThrow(new IllegalStateException("storage unavailable"));

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs()).hasSize(12);
        assertThat(result.jobs().get(0).descriptionPath()).isNull();
        assertThat(result.jobs().get(11).descriptionPath()).isEqualTo("description-1011");
        assertThat(result.errors()).singleElement().asString().contains("1000", "storage unavailable");
        ScrapeCompletedEvent event = new ScrapeCompletedEvent(result.runId(), result.companyId(),
                result.startedAt(), result.completedAt(), result.searchPagesFetched(), result.detailPagesFetched(),
                result.blobPaths(), result.errors(), result.jobs());
        byte[] kafkaMessage = mapper.writeValueAsBytes(event);
        assertThat(kafkaMessage.length).isLessThan(1048576);
        assertThat(new String(kafkaMessage, StandardCharsets.UTF_8)).doesNotContain("Detailed requirements");
    }

    private static String generatedPage(ObjectMapper mapper, int start, int end, String text) throws Exception {
        var jobs = java.util.stream.IntStream.range(start, end)
                .mapToObj(i -> java.util.Map.of("data", java.util.Map.of(
                        "req_id", Integer.toString(1000 + i), "slug", Integer.toString(1000 + i),
                        "title", "Engineer", "description", "<p>" + text + "</p>")))
                .toList();
        return mapper.writeValueAsString(java.util.Map.of("totalCount", 12, "jobs", jobs));
    }

    @Test
    void handlesEmptyResults() throws Exception {
        HttpResponse<String> empty = response(200, "{\"totalCount\":0,\"jobs\":[]}");
        when(httpClient.get(uri(1))).thenReturn(empty);
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(1);
    }

    @Test
    void reportsHttpAndMalformedResponseFailures() throws Exception {
        HttpResponse<String> unavailable = response(503, "");
        HttpResponse<String> malformed = response(200, "{\"jobs\":[]}");
        HttpResponse<String> missingId = response(200, "{\"totalCount\":1,\"jobs\":[{\"data\":{\"title\":\"Developer\"}}]}");
        when(httpClient.get(uri(1))).thenReturn(unavailable, malformed, missingId);
        assertThat(scraper.scrape(context()).errors()).singleElement().asString().contains("HTTP 503");
        assertThat(scraper.scrape(context()).errors()).singleElement().asString().contains("Invalid jobs API response");
        assertThat(scraper.scrape(context()).errors()).singleElement().asString().contains("Missing job req_id");
        verify(storage, never()).writeSearchPage(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void reportsUnexpectedEmptyPageAndKeepsEarlierJobs() throws Exception {
        HttpResponse<String> first = response(200, fixture("search-results-page1.json"));
        HttpResponse<String> empty = response(200, "{\"totalCount\":11,\"jobs\":[]}");
        when(httpClient.get(uri(1))).thenReturn(first);
        when(httpClient.get(uri(2))).thenReturn(empty);
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.jobs()).hasSize(2);
        assertThat(result.errors()).singleElement().asString().contains("empty before reaching the reported total");
    }

    @Test
    void registersScraperThroughComponentScanning() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScanConfiguration.class)
                .withBean(JobSearchHttpClient.class, () -> httpClient)
                .withBean(RawScrapeStorage.class, () -> storage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JobScraperProperties.class, JobScraperProperties::new)
                .run(context -> {
                    FirstCitizensScraper bean = context.getBean(FirstCitizensScraper.class);
                    assertThat(context.getBean(CompanyScraperRegistry.class).find(COMPANY_ID)).containsSame(bean);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {FirstCitizensScraper.class, CompanyScraperRegistry.class})
    static class ScanConfiguration {
    }

    private static ScrapeContext context() {
        return new ScrapeContext("run-1", COMPANY_ID, Instant.parse("2026-11-22T00:00:00Z"));
    }

    private static URI uri(int page) {
        return URI.create("https://firstcitizens.jibeapply.com/api/jobs?page=" + page
                + "&categories=Information%20Technology&sortBy=posted_date&descending=true");
    }

    private static String fixture(String name) throws Exception {
        try (var stream = Objects.requireNonNull(FirstCitizensScraperTest.class.getResourceAsStream(
                "/fixtures/firstcitizens/" + name))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }
}
