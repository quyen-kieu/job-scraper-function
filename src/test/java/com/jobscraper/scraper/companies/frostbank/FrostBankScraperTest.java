package com.jobscraper.scraper.companies.frostbank;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.mockito.ArgumentCaptor;
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
class FrostBankScraperTest {
    private static final String COMPANY_ID = "frost-bank";
    private static final String BASE = "https://careers.frostbank.com/us/en/";
    private static final String FIRST_JOB = BASE + "job/FRBAUSR260062EXTERNALENUS";

    @Mock
    private JobSearchHttpClient httpClient;
    @Mock
    private RawScrapeStorage storage;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private JobScraperProperties properties;
    private FrostBankScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        scraper = new FrostBankScraper(httpClient, storage, objectMapper, properties);
        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(invocation -> "search-" + invocation.getArgument(2));
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> "detail-" + invocation.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString())).thenReturn("summary");
    }

    @Test
    void fetchesPagesDetailsAndStructuredJobs() throws Exception {
        properties.setMaxDetailPagesPerRun(1);
        HttpResponse<String> page1 = response(200, fixture("search-page1.html"));
        HttpResponse<String> page2 = response(200, fixture("search-page2.html"));
        String detailHtml = fixture("job-detail.html");
        HttpResponse<String> detail = response(200, detailHtml);
        when(httpClient.get(searchUri(0))).thenReturn(page1);
        when(httpClient.get(searchUri(10))).thenReturn(page2);
        when(httpClient.get(URI.create(FIRST_JOB))).thenReturn(detail);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.searchPagesFetched()).isEqualTo(2);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(result.blobPaths()).containsExactly("search-1", "search-2", "detail-R260062", "summary");
        assertThat(result.jobs()).extracting(ScrapedJob::externalJobId).containsExactly("R260062", "R261325", "R262361");
        assertThat(result.jobs().get(0)).isEqualTo(new ScrapedJob("R260062", "Engineer III - AI",
                "San Antonio, TX - One Frost", LocalDate.of(2026, 1, 13), FIRST_JOB, "$100,000 - $150,000 per year"));
        assertThat(result.jobs().get(1).salaryRange()).isNull();
        assertThat(result.jobs().get(2).datePosted()).isEqualTo(LocalDate.of(2026, 11, 20));
        verify(httpClient, never()).get(searchUri(20));
        verify(storage).writeJobDetail(COMPANY_ID, "run-1", "R260062", detailHtml);

        ArgumentCaptor<String> searchJson = ArgumentCaptor.forClass(String.class);
        verify(storage).writeSearchPage(eq(COMPANY_ID), eq("run-1"), eq(1), searchJson.capture());
        JsonNode stored = objectMapper.readTree(searchJson.getValue());
        assertThat(stored.path("totalHits").intValue()).isEqualTo(11);
        assertThat(stored.path("data").path("jobs").size()).isEqualTo(2);
    }

    @Test
    void respectsSearchAndDetailRequestCapsEvenWhenDetailsFail() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        properties.setMaxDetailPagesPerRun(1);
        HttpResponse<String> page1 = response(200, fixture("search-page1.html"));
        HttpResponse<String> error = response(503, "");
        when(httpClient.get(searchUri(0))).thenReturn(page1);
        when(httpClient.get(URI.create(FIRST_JOB))).thenReturn(error);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs()).hasSize(2);
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isZero();
        assertThat(result.errors()).singleElement().asString().contains("R260062", "HTTP 503");
        verify(httpClient, never()).get(searchUri(10));
        verify(httpClient, never()).get(URI.create(BASE + "job/FRBAUSR261325EXTERNALENUS"));
    }

    @Test
    void reportsSearchHttpAndSchemaErrorsWithoutDroppingEarlierJobs() throws Exception {
        properties.setMaxDetailPagesPerRun(0);
        HttpResponse<String> page1 = response(200, fixture("search-page1.html"));
        HttpResponse<String> malformed = response(200, "<html><body>No data</body></html>");
        when(httpClient.get(searchUri(0))).thenReturn(page1);
        when(httpClient.get(searchUri(10))).thenReturn(malformed);

        ScrapeResult result = scraper.scrape(context());
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.jobs()).hasSize(2);
        assertThat(result.errors()).singleElement().asString().contains("Search page 2", "Missing Frost Bank embedded data");
    }

    @Test
    void reportsMalformedOrMismatchedDetailsAndKeepsSearchJobs() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        properties.setMaxDetailPagesPerRun(1);
        HttpResponse<String> page = response(200, fixture("search-page1.html"));
        HttpResponse<String> wrongDetail = response(200, fixture("job-detail.html").replace("R260062", "R999999"));
        when(httpClient.get(searchUri(0))).thenReturn(page);
        when(httpClient.get(URI.create(FIRST_JOB))).thenReturn(wrongDetail);
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.jobs()).hasSize(2);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.jobs().get(0).salaryRange()).isNull();
        assertThat(result.errors()).singleElement().asString().contains("Detail parse failed", "R260062");
    }

    @Test
    void handlesEmptyAndInvalidSearchResults() throws Exception {
        properties.setMaxDetailPagesPerRun(0);
        HttpResponse<String> empty = response(200, "<script>phApp.ddo = "
                + "{\"eagerLoadRefineSearch\":{\"status\":200,\"totalHits\":0,\"data\":{\"jobs\":[]}}};</script>");
        HttpResponse<String> invalid = response(200, "<script>phApp.ddo = "
                + "{\"eagerLoadRefineSearch\":{\"status\":200,\"data\":{\"jobs\":[]}}};</script>");
        when(httpClient.get(searchUri(0))).thenReturn(empty, invalid);

        ScrapeResult emptyResult = scraper.scrape(context());
        assertThat(emptyResult.jobs()).isEmpty();
        assertThat(emptyResult.errors()).isEmpty();
        assertThat(emptyResult.searchPagesFetched()).isEqualTo(1);
        assertThat(scraper.scrape(context()).errors()).singleElement().asString().contains("Invalid Frost Bank search response");
    }

    @Test
    void registersScraperThroughComponentScanning() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScanConfiguration.class)
                .withBean(JobSearchHttpClient.class, () -> httpClient)
                .withBean(RawScrapeStorage.class, () -> storage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JobScraperProperties.class, JobScraperProperties::new)
                .run(context -> assertThat(context.getBean(CompanyScraperRegistry.class).find(COMPANY_ID))
                        .containsSame(context.getBean(FrostBankScraper.class)));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {FrostBankScraper.class, CompanyScraperRegistry.class})
    static class ScanConfiguration {
    }

    private static ScrapeContext context() {
        return new ScrapeContext("run-1", COMPANY_ID, Instant.parse("2026-11-22T00:00:00Z"));
    }

    private static URI searchUri(int offset) {
        return URI.create(BASE + "c/technology-digital-jobs?from=" + offset);
    }

    private static String fixture(String name) throws Exception {
        try (var stream = Objects.requireNonNull(FrostBankScraperTest.class.getResourceAsStream(
                "/fixtures/frostbank/" + name))) {
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
