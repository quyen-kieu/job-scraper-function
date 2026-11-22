package com.jobscraper.scraper.companies.firstcitizens;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
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
                "$95,000 and $125,000"));
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
