package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static com.jobscraper.scraper.companies.oracle.OracleRecruitingResponseParserTest.loadFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OracleRecruitingScraperTest {

    private static final OracleRecruitingSite SITE = new OracleRecruitingSite(
            "gm-financial", "fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com", "CX_1", "software development");

    @Mock
    private JobSearchHttpClient httpClient;

    @Mock
    private RawScrapeStorage storage;

    private JobScraperProperties properties;
    private OracleRecruitingScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        properties.setMaxSearchPagesPerRun(3);
        properties.setMaxDetailPagesPerRun(10);
        scraper = new OracleRecruitingScraper(
                SITE, httpClient, storage, new ObjectMapper().findAndRegisterModules(), properties);

        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> "mock://search/" + inv.getArgument(2));
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "mock://detail/" + inv.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString()))
                .thenReturn("mock://summary");
    }

    @Test
    void shouldReturnConfiguredCompanyId() {
        assertThat(scraper.companyId()).isEqualTo("gm-financial");
    }

    @Test
    void shouldScrapeSinglePageWithDetailsAndSalary() throws Exception {
        HttpResponse<String> search = response(singlePageSearch());
        HttpResponse<String> withSalary = response(loadFixture("job-detail-with-salary.json"));
        HttpResponse<String> withoutSalary = response(loadFixture("job-detail-without-salary.json"));
        when(httpClient.get(searchUri())).thenReturn(search);
        when(httpClient.get(detailUriFor("261186"))).thenReturn(withSalary);
        when(httpClient.get(detailUriFor("261197"))).thenReturn(withoutSalary);
        when(httpClient.get(detailUriFor("261150"))).thenReturn(withoutSalary);

        ScrapeResult result = scraper.scrape(new ScrapeContext("run-1", "gm-financial", Instant.now()));

        assertThat(result.companyId()).isEqualTo("gm-financial");
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();
        assertThat(result.blobPaths()).hasSize(5); // 1 search page + 3 details + 1 run summary
        assertThat(result.jobs()).extracting(ScrapedJob::externalJobId).containsExactly("261197", "261186", "261150");

        ScrapedJob actuarialManager = result.jobs().get(1);
        assertThat(actuarialManager.title()).isEqualTo("Actuarial Manager");
        assertThat(actuarialManager.datePosted()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(actuarialManager.salaryRange()).isEqualTo("$115,000-$213,000");
        assertThat(actuarialManager.url()).isEqualTo(
                "https://fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1/job/261186");
        assertThat(result.jobs().get(0).salaryRange()).isNull();

        verify(storage).writeSearchPage(eq("gm-financial"), eq("run-1"), eq(1), anyString());
        verify(storage).writeRunSummary(eq("gm-financial"), eq("run-1"), anyString());
    }

    @Test
    void shouldPageUntilSearchPageCapAndDeduplicateRequisitions() throws Exception {
        properties.setMaxSearchPagesPerRun(2);
        properties.setMaxDetailPagesPerRun(0);
        // Real fixture declares TotalJobsCount=53, so more pages exist than the cap allows.
        HttpResponse<String> search = response(loadFixture("search-results-page1.json"));
        when(httpClient.get(searchUri())).thenReturn(search);

        ScrapeResult result = scraper.scrape(new ScrapeContext("run-2", "gm-financial", Instant.now()));

        assertThat(result.searchPagesFetched()).isEqualTo(2);
        assertThat(result.detailPagesFetched()).isZero();
        assertThat(result.jobs()).hasSize(3);
        verify(httpClient).get(SITE.searchUri(0, OracleRecruitingScraper.PAGE_SIZE));
        verify(httpClient).get(SITE.searchUri(3, OracleRecruitingScraper.PAGE_SIZE));
        verify(storage, times(2)).writeSearchPage(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void shouldIsolateDetailFailuresWithoutFailingWholeRun() throws Exception {
        HttpResponse<String> search = response(singlePageSearch());
        HttpResponse<String> withSalary = response(loadFixture("job-detail-with-salary.json"));
        HttpResponse<String> unavailable = errorResponse(503);
        when(httpClient.get(searchUri())).thenReturn(search);
        when(httpClient.get(detailUriFor("261197"))).thenThrow(new RuntimeException("connection reset"));
        when(httpClient.get(detailUriFor("261186"))).thenReturn(withSalary);
        when(httpClient.get(detailUriFor("261150"))).thenReturn(unavailable);

        ScrapeResult result = scraper.scrape(new ScrapeContext("run-3", "gm-financial", Instant.now()));

        assertThat(result.jobs()).hasSize(3);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("261197").contains("connection reset");
        assertThat(result.errors().get(1)).contains("261150").contains("HTTP 503");
        assertThat(result.jobs().get(1).salaryRange()).isEqualTo("$115,000-$213,000");
    }

    @Test
    void shouldRecordErrorAndReturnNoJobsWhenSearchFails() throws Exception {
        HttpResponse<String> failure = errorResponse(500);
        when(httpClient.get(searchUri())).thenReturn(failure);

        ScrapeResult result = scraper.scrape(new ScrapeContext("run-4", "gm-financial", Instant.now()));

        assertThat(result.searchPagesFetched()).isZero();
        assertThat(result.jobs()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("Search page 1 fetch failed");
        verify(storage, never()).writeSearchPage(anyString(), anyString(), anyInt(), anyString());
    }

    private static String singlePageSearch() throws Exception {
        return loadFixture("search-results-page1.json").replace("\"TotalJobsCount\": 53", "\"TotalJobsCount\": 3");
    }

    private static URI searchUri() {
        return ArgumentMatchers.argThat(uri -> uri != null && uri.toString().contains("recruitingCEJobRequisitions?"));
    }

    private static URI detailUriFor(String id) {
        return ArgumentMatchers.argThat(uri -> uri != null
                && uri.toString().contains("recruitingCEJobRequisitionDetails")
                && uri.toString().contains("%22" + id + "%22"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(200);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> errorResponse(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        return response;
    }
}







