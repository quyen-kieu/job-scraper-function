package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
class McKessonScraperTest {
    private static final String ONE_JOB = """
            {"title":"Engineer","externalPath":"/job/Texas/Engineer_JR100",
             "locationsText":"2 Locations","postedOn":"Posted 30+ Days Ago","bulletFields":["JR100"]}
            """;

    @Mock private JobSearchHttpClient httpClient;
    @Mock private RawScrapeStorage storage;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JobScraperProperties properties;
    private McKessonScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        scraper = new McKessonScraper(httpClient, storage, mapper, properties);
        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> "search/" + inv.getArgument(2));
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "detail/" + inv.getArgument(2));
        lenient().when(storage.writeDescription(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "description/" + inv.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString())).thenReturn("summary");
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobDescription\":\"<p>Job description</p>\"}}");
        lenient().when(httpClient.get(any())).thenReturn(detail);
    }

    @Test
    void filtersSearchAndExtractsWorkdayDetailDateAndSalary() throws Exception {
        HttpResponse<String> searchResponse = response(200, search(1, ONE_JOB));
        HttpResponse<String> detailResponse = response(200, """
                {"jobPostingInfo":{"startDate":"2026-11-20",
                  "jobDescription":"<p>Pay range: $120,000 - $180,000</p>"}}
                """);
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(detailResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.companyId()).isEqualTo("mckesson");
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).hasSize(1);
        assertThat(result.jobs().get(0).externalJobId()).isEqualTo("JR100");
        assertThat(result.jobs().get(0).url())
                .isEqualTo("https://mckesson.wd3.myworkdayjobs.com/en-US/External_Careers/job/Texas/Engineer_JR100");
        assertThat(result.jobs().get(0).datePosted()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(result.jobs().get(0).salaryRange()).isEqualTo("$120,000 - $180,000");
        assertThat(result.jobs().get(0).descriptionPath()).isEqualTo("description/JR100");
        assertThat(result.jobs().get(0).descriptionFormat()).isEqualTo(DescriptionFormat.HTML);
        verify(storage).writeDescription("mckesson", "run-123", "JR100", "<p>Pay range: $120,000 - $180,000</p>");
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(eq(URI.create(
                "https://mckesson.wd3.myworkdayjobs.com/wday/cxs/mckesson/External_Careers/jobs")), request.capture());
        JsonNode json = mapper.readTree(request.getValue());
        assertThat(json.path("appliedFacets").path("Location_Region_State_Province").get(0).asText())
                .isEqualTo(properties.getMckessonStateFacetId());
        assertThat(json.path("appliedFacets").path("jobFamilyGroup").get(0).asText())
                .isEqualTo(properties.getMckessonJobFamilyFacetId());
        assertThat(json.path("appliedFacets").path("Location_Country").get(0).asText())
                .isEqualTo(properties.getMckessonCountryFacetId());
        assertThat(json.path("limit").asInt()).isEqualTo(20);
        assertThat(json.path("offset").asInt()).isZero();
        verify(storage).writeJobDetail(eq("mckesson"), eq("run-123"), eq("JR100"), anyString());
    }

    @Test
    void storesPlainTextWhenWorkdayReturnsNoHtml() throws Exception {
        HttpResponse<String> searchResponse = response(200, search(1, ONE_JOB));
        HttpResponse<String> detailResponse = response(200,
                "{\"jobPostingInfo\":{\"jobDescription\":\"Build systems\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(detailResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs().get(0).descriptionFormat()).isEqualTo(DescriptionFormat.PLAIN_TEXT);
        verify(storage).writeDescription("mckesson", "run-123", "JR100", "Build systems");
    }

    @Test
    void obtainsMissingRequisitionIdFromWorkdayPath() throws Exception {
        String missingBullet = ONE_JOB.replace(",\"bulletFields\":[\"JR100\"]", "");
        HttpResponse<String> searchResponse = response(200, search(1, missingBullet));
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).extracting("externalJobId").containsExactly("JR100");
        verify(storage).writeDescription(eq("mckesson"), eq("run-123"), eq("JR100"), anyString());
    }

    @Test
    void rejectsMissingRequisitionIdWhenPathHasNoUnambiguousId() throws Exception {
        String missingId = ONE_JOB.replace(",\"bulletFields\":[\"JR100\"]", "")
                .replace("Engineer_JR100", "Engineer");
        HttpResponse<String> searchResponse = response(200, search(1, missingId));
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("job ID");
        verify(httpClient, never()).get(any());
    }

    @Test
    void paginatesUsingFirstPageTotalWhenLaterPageTotalIsZero() throws Exception {
        String secondJob = ONE_JOB.replace("JR100", "JR101");
        HttpResponse<String> firstResponse = response(200, search(21, ONE_JOB));
        HttpResponse<String> secondResponse = response(200, search(0, secondJob));
        when(httpClient.postJson(any(), anyString()))
                .thenReturn(firstResponse, secondResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(2);
        assertThat(result.jobs()).extracting("externalJobId").containsExactly("JR100", "JR101");
        assertThat(result.jobs().get(0).datePosted()).isNull();
        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).postJson(any(), requests.capture());
        assertThat(mapper.readTree(requests.getAllValues().get(1)).path("offset").asInt()).isEqualTo(20);
    }

    @Test
    void unlimitedSearchFetchesBeyondThreePagesUntilReportedTotal() throws Exception {
        properties.setMaxSearchPagesPerRun(0);
        HttpResponse<String> first = response(200, search(61, ONE_JOB));
        HttpResponse<String> second = response(200, search(0, ONE_JOB.replace("JR100", "JR101")));
        HttpResponse<String> third = response(200, search(0, ONE_JOB.replace("JR100", "JR102")));
        HttpResponse<String> fourth = response(200, search(0, ONE_JOB.replace("JR100", "JR103")));
        when(httpClient.postJson(any(), anyString())).thenReturn(first, second, third, fourth);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(4);
        assertThat(result.jobs()).extracting("externalJobId").containsExactly("JR100", "JR101", "JR102", "JR103");
        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(4)).postJson(any(), requests.capture());
        assertThat(mapper.readTree(requests.getAllValues().get(3)).path("offset").asInt()).isEqualTo(60);
    }

    @Test
    void attemptsEveryDetailEvenWhenRequestsFail() throws Exception {
        HttpResponse<String> searchResponse = response(200, search(2, ONE_JOB + ","
                + ONE_JOB.replace("JR100", "JR101")));
        HttpResponse<String> detailResponse = response(500, "failed");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(detailResponse);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs()).hasSize(2);
        assertThat(result.detailPagesFetched()).isZero();
        assertThat(result.errors()).hasSize(2).allMatch(error -> error.contains("HTTP 500"));
        verify(httpClient, times(2)).get(any());
    }

    @Test
    void processesMoreThanTenJobsEvenAfterARequestFailure() throws Exception {
        String jobs = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> ONE_JOB.replace("JR100", "JR" + (100 + i)))
                .collect(java.util.stream.Collectors.joining(","));
        HttpResponse<String> searchResponse = response(200, search(12, jobs));
        HttpResponse<String> failure = response(503, "");
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobDescription\":\"<p>Build systems</p>\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(failure, detail);

        ScrapeResult result = scraper.scrape(context());

        assertThat(result.jobs()).hasSize(12);
        assertThat(result.detailPagesFetched()).isEqualTo(11);
        assertThat(result.jobs().get(11).descriptionPath()).isEqualTo("description/JR111");
        assertThat(result.errors()).singleElement().asString().contains("JR100", "503");
        verify(httpClient, times(12)).get(any());
    }

    @Test
    void respectsSearchCapAndInterpretsRelativeDates() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        String yesterday = ONE_JOB.replace("JR100", "JR101")
                .replace("Posted 30+ Days Ago", "Posted Yesterday");
        String daysAgo = ONE_JOB.replace("JR100", "JR102")
                .replace("Posted 30+ Days Ago", "Posted 4 Days Ago");
        HttpResponse<String> searchResponse = response(200, search(100, ONE_JOB + "," + yesterday + "," + daysAgo));
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);

        ScrapeResult result = scraper.scrape(context());

        LocalDate today = result.startedAt().atZone(ZoneOffset.UTC).toLocalDate();
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.jobs().get(0).datePosted()).isNull();
        assertThat(result.jobs().get(1).datePosted()).isEqualTo(today.minusDays(1));
        assertThat(result.jobs().get(2).datePosted()).isEqualTo(today.minusDays(4));
        verify(httpClient, times(1)).postJson(any(), anyString());
    }

    @Test
    void preservesInterruptWhenSearchIsInterrupted() throws Exception {
        when(httpClient.postJson(any(), anyString())).thenThrow(new InterruptedException("interrupted"));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> scraper.scrape(context()))
                    .isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void reportsRejectedFilterAndDoesNotFallBackToUnfilteredSearch() throws Exception {
        HttpResponse<String> searchResponse = response(400, "bad filter");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.jobs()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("HTTP 400");
        verify(httpClient, never()).get(any());
    }

    @Test
    void rejectsMalformedFilterBeforeSendingRequest() throws Exception {
        properties.setMckessonStateFacetId("bad");
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.errors()).singleElement().asString().contains("facet IDs");
        verify(httpClient, never()).postJson(any(), anyString());
    }

    @Test
    void reportsInvalidSearchResponse() throws Exception {
        HttpResponse<String> searchResponse = response(200, "{\"unexpected\":[]}");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        ScrapeResult result = scraper.scrape(context());
        assertThat(result.errors()).singleElement().asString().contains("Invalid Workday search response");
        assertThat(result.searchPagesFetched()).isZero();
    }

    private static String search(int total, String jobs) {
        return "{\"total\":" + total + ",\"jobPostings\":[" + jobs + "]}";
    }

    private static ScrapeContext context() {
        return new ScrapeContext("run-123", "mckesson", Instant.now());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }
}
