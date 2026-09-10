package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McKessonScraperTest {

    private ObjectMapper objectMapper;

    @Mock
    private JobSearchHttpClient httpClient;

    @Mock
    private RawScrapeStorage storage;

    private JobScraperProperties properties;
    private McKessonScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        properties.setMaxSearchPagesPerRun(3);
        properties.setMaxDetailPagesPerRun(10);
        scraper = new McKessonScraper(httpClient, storage, objectMapper, properties);

        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> "mock://search/" + inv.getArgument(2));
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "mock://detail/" + inv.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString()))
                .thenReturn("mock://summary");
    }

    @Test
    void shouldReturnCorrectCompanyId() {
        assertThat(scraper.companyId()).isEqualTo("mckesson");
    }

    @Test
    void shouldFetchSinglePageAndDetailPagesWhenTotalPagesIsOne() throws Exception {
        String singlePageJson = loadFixture("search-results-page1.json")
                .replace("data-total-pages=\\\"3\\\"", "data-total-pages=\\\"1\\\"");
        HttpResponse<String> searchResponse = textResponse(singlePageJson);
        when(httpClient.get(any(URI.class))).thenReturn(searchResponse);

        String detailHtml = loadFixture("job-detail-real.html");
        HttpResponse<String> detailResponse = textResponse(detailHtml);
        when(httpClient.get(argThatDetailUri())).thenReturn(detailResponse);

        ScrapeContext context = new ScrapeContext("run-123", "mckesson", Instant.now());
        ScrapeResult result = scraper.scrape(context);

        assertThat(result.runId()).isEqualTo("run-123");
        assertThat(result.companyId()).isEqualTo("mckesson");
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        assertThat(result.blobPaths()).hasSize(4); // 1 search page + 2 detail pages + 1 run summary

        verify(storage, times(1)).writeSearchPage(eq("mckesson"), eq("run-123"), eq(1), anyString());
        verify(storage, times(1)).writeRunSummary(eq("mckesson"), eq("run-123"), anyString());
    }

    @Test
    void shouldStopAtSearchPageCapEvenWhenMorePagesExist() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        properties.setMaxDetailPagesPerRun(0);
        String threePageJson = loadFixture("search-results-page1.json"); // declares data-total-pages="3"
        HttpResponse<String> searchResponse = textResponse(threePageJson);
        when(httpClient.get(any(URI.class))).thenReturn(searchResponse);

        ScrapeContext context = new ScrapeContext("run-cap", "mckesson", Instant.now());
        ScrapeResult result = scraper.scrape(context);

        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isZero();
        verify(storage, times(1)).writeSearchPage(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void shouldIsolateDetailPageFailuresWithoutFailingWholeRun() throws Exception {
        properties.setMaxDetailPagesPerRun(10);
        String singlePageJson = loadFixture("search-results-page1.json")
                .replace("data-total-pages=\\\"3\\\"", "data-total-pages=\\\"1\\\"");
        HttpResponse<String> searchResponse = textResponse(singlePageJson);
        HttpResponse<String> detailResponse = textResponse(loadFixture("job-detail-real.html"));
        when(httpClient.get(any(URI.class)))
                .thenReturn(searchResponse)
                .thenThrow(new RuntimeException("connection reset"))
                .thenReturn(detailResponse);

        ScrapeContext context = new ScrapeContext("run-err", "mckesson", Instant.now());
        ScrapeResult result = scraper.scrape(context);

        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("connection reset");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> textResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    private URI argThatDetailUri() {
        return org.mockito.ArgumentMatchers.argThat(uri -> uri != null && uri.getPath().startsWith("/en/job/"));
    }

    private String loadFixture(String filename) throws Exception {
        return Files.readString(Paths.get("src/test/resources/fixtures/mckesson/" + filename));
    }
}


