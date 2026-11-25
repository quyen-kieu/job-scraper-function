package com.jobscraper.scraper.companies.cvs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
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
import java.util.List;

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
class CvsScraperTest {
    private static final String JOB = """
            {"title":"Engineer","externalPath":"/job/Remote/Engineer_R1097216",
             "locationsText":"4 Locations","postedOn":"Posted Today","bulletFields":["R1097216"]}
            """;

    @Mock private JobSearchHttpClient httpClient;
    @Mock private RawScrapeStorage storage;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JobScraperProperties properties;
    private CvsScraper scraper;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        scraper = new CvsScraper(httpClient, storage, mapper, properties);
        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> "search/" + inv.getArgument(2));
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "detail/" + inv.getArgument(2));
        lenient().when(storage.writeDescription(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "description/" + inv.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString())).thenReturn("summary");
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobDescription\":\"<p>Build systems</p>\"}}");
        lenient().when(httpClient.get(any())).thenReturn(detail);
    }

    @Test
    void appliesAllRequestedFacetsAndStoresSourceDescription() throws Exception {
        HttpResponse<String> searchResponse = response(200, search(1, JOB));
        HttpResponse<String> detail = response(200, """
                {"jobPostingInfo":{"startDate":"2026-11-25","jobDescription":"<p>Build systems</p>"}}
                """);
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(detail);

        var result = scraper.scrape(context());

        assertThat(result.companyId()).isEqualTo("cvs");
        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.jobs()).hasSize(1);
        var job = result.jobs().get(0);
        assertThat(job.externalJobId()).isEqualTo("R1097216");
        assertThat(job.url()).isEqualTo("https://cvshealth.wd1.myworkdayjobs.com/CVS_Health_Careers"
                + "/job/Remote/Engineer_R1097216");
        assertThat(job.datePosted()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(job.descriptionPath()).isEqualTo("description/R1097216");
        assertThat(job.descriptionFormat()).isEqualTo(DescriptionFormat.HTML);
        verify(httpClient).get(URI.create("https://cvshealth.wd1.myworkdayjobs.com"
                + "/wday/cxs/cvshealth/CVS_Health_Careers/job/Remote/Engineer_R1097216"));
        verify(storage).writeDescription("cvs", "run-123", "R1097216", "<p>Build systems</p>");

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(eq(URI.create("https://cvshealth.wd1.myworkdayjobs.com"
                + "/wday/cxs/cvshealth/CVS_Health_Careers/jobs")), request.capture());
        JsonNode json = mapper.readTree(request.getValue());
        assertThat(json.path("appliedFacets").path("jobFamilyGroup").get(0).asText())
                .isEqualTo("e65dbadf6a50100168ed86fe4cf50001");
        assertThat(json.path("appliedFacets").path("locations"))
                .extracting(JsonNode::asText)
                .containsExactly("69b7f7384b5e1008e9c744772f180000",
                        "e75b3cfda6d41000edbef9d1f17b0000", "e75b3cfda6d41000edbdcbb1c2b50000",
                        "e75b3cfda6d41000edbc9e0488440000", "e75b3cfda6d41000edc098b35b130000",
                        "e75b3cfda6d41000edc32657f9160000");
        assertThat(json.path("limit").asInt()).isEqualTo(20);
        assertThat(json.path("offset").asInt()).isZero();
    }

    @Test
    void paginatesAndFallsBackToPathId() throws Exception {
        String second = JOB.replace("R1097216", "R1097217")
                .replace(",\"bulletFields\":[\"R1097217\"]", "");
        HttpResponse<String> first = response(200, search(21, JOB));
        HttpResponse<String> next = response(200, search(0, second));
        when(httpClient.postJson(any(), anyString())).thenReturn(first, next);

        var result = scraper.scrape(context());

        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(2);
        assertThat(result.jobs()).extracting("externalJobId").containsExactly("R1097216", "R1097217");
        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(httpClient, times(2)).postJson(any(), requests.capture());
        assertThat(mapper.readTree(requests.getAllValues().get(1)).path("offset").asInt()).isEqualTo(20);
    }

    @Test
    void refusesInvalidOrEmptyFacetsWithoutSearchingUnfiltered() throws Exception {
        properties.setCvsLocationFacetIds(List.of());
        var empty = scraper.scrape(context());
        assertThat(empty.errors()).singleElement().asString().contains("facet IDs");
        properties.setCvsLocationFacetIds(List.of("bad-id"));
        var invalid = scraper.scrape(context());
        assertThat(invalid.errors()).singleElement().asString().contains("facet IDs");
        properties.setCvsLocationFacetIds(List.of("69b7f7384b5e1008e9c744772f180000"));
        properties.setCvsJobFamilyFacetId("bad-id");
        var badFamily = scraper.scrape(context());
        assertThat(badFamily.errors()).singleElement().asString().contains("facet IDs");
        verify(httpClient, never()).postJson(any(), anyString());
    }

    @Test
    void continuesAfterDetailFailure() throws Exception {
        HttpResponse<String> searchResponse = response(200, search(2, JOB + ","
                + JOB.replace("R1097216", "R1097217")));
        HttpResponse<String> failure = response(503, "");
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobDescription\":\"Build systems\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(searchResponse);
        when(httpClient.get(any())).thenReturn(failure, detail);

        var result = scraper.scrape(context());

        assertThat(result.errors()).singleElement().asString().contains("R1097216", "503");
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.jobs().get(1).descriptionFormat()).isEqualTo(DescriptionFormat.PLAIN_TEXT);
        verify(storage).writeDescription("cvs", "run-123", "R1097217", "Build systems");
    }

    private static String search(int total, String jobs) {
        return "{\"total\":" + total + ",\"jobPostings\":[" + jobs + "]}";
    }

    private static ScrapeContext context() {
        return new ScrapeContext("run-123", "cvs", Instant.now());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }
}
