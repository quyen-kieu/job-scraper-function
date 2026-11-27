package com.jobscraper.scraper.companies.workday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.companies.capitalone.CapitalOneScraper;
import com.jobscraper.scraper.companies.citi.CitiScraper;
import com.jobscraper.scraper.companies.fanniemae.FannieMaeScraper;
import com.jobscraper.scraper.companies.frostbank.FrostBankScraper;
import com.jobscraper.scraper.companies.humana.HumanaScraper;
import com.jobscraper.scraper.registry.CompanyScraperRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayCompanyScrapersTest {
    @Mock private JobSearchHttpClient httpClient;
    @Mock private RawScrapeStorage storage;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JobScraperProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobScraperProperties();
        properties.setRequestDelay(Duration.ZERO);
        lenient().when(storage.writeSearchPage(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("search");
        lenient().when(storage.writeJobDetail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("detail");
        lenient().when(storage.writeDescription(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "description/" + inv.getArgument(2));
        lenient().when(storage.writeRunSummary(anyString(), anyString(), anyString())).thenReturn("summary");
    }

    @ParameterizedTest
    @MethodSource("sites")
    void appliesAllFacetsAndStoresSourceDescriptions(Site site) throws Exception {
        String path = "/job/Texas/Engineer_" + site.id() + "-1";
        HttpResponse<String> search = response(200, "{\"total\":1,\"jobPostings\":[{"
                + "\"title\":\"Engineer\",\"externalPath\":\"" + path + "\","
                + "\"locationsText\":\"Texas\",\"postedOn\":\"Posted Today\","
                + "\"bulletFields\":[\"" + site.id() + "\"]}]}");
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobReqId\":\"" + site.id()
                + "\",\"startDate\":\"2026-11-25\",\"jobDescription\":\"<p>Build systems</p>\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);
        when(httpClient.get(any())).thenReturn(detail);

        var result = scraper(site.companyId()).scrape(context(site));

        assertThat(result.errors()).isEmpty();
        assertThat(result.searchPagesFetched()).isEqualTo(1);
        assertThat(result.searchComplete()).isTrue();
        assertThat(result.detailPagesFetched()).isEqualTo(1);
        assertThat(result.jobs()).hasSize(1);
        var job = result.jobs().get(0);
        assertThat(job.externalJobId()).isEqualTo(site.id());
        assertThat(job.url()).isEqualTo(site.publicBase() + path);
        assertThat(job.datePosted()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(job.descriptionPath()).isEqualTo("description/" + site.id());
        assertThat(job.descriptionFormat()).isEqualTo(DescriptionFormat.HTML);
        verify(httpClient).get(URI.create(site.apiBase() + path));
        verify(storage).writeDescription(site.companyId(), "run-123", site.id(), "<p>Build systems</p>");

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(eq(URI.create(site.apiBase() + "/jobs")), request.capture());
        var json = mapper.readTree(request.getValue());
        assertThat(json.path("appliedFacets")).isEqualTo(mapper.valueToTree(site.facets()));
        assertThat(json.path("limit").asInt()).isEqualTo(20);
        assertThat(json.path("offset").asInt()).isZero();
    }

    @ParameterizedTest
    @MethodSource("sites")
    void derivesRequisitionIdFromSuffixedPathWhenBulletIsMissing(Site site) throws Exception {
        String body = "{\"total\":1,\"jobPostings\":[{\"title\":\"Engineer\","
                + "\"externalPath\":\"/job/Texas/Engineer_" + site.id() + "-1\"}]}";
        HttpResponse<String> search = response(200, body);
        HttpResponse<String> detail = response(200, "{\"jobPostingInfo\":{\"jobReqId\":\"" + site.id()
                + "\",\"jobDescription\":\"Build systems\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);
        when(httpClient.get(any())).thenReturn(detail);

        var result = scraper(site.companyId()).scrape(context(site));

        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).extracting("externalJobId").containsExactly(site.id());
        assertThat(result.jobs().get(0).descriptionFormat()).isEqualTo(DescriptionFormat.PLAIN_TEXT);
    }

    @ParameterizedTest
    @MethodSource("sites")
    void rejectsMismatchedDetailIds(Site site) throws Exception {
        HttpResponse<String> search = response(200, "{\"total\":1,\"jobPostings\":[{\"title\":\"Engineer\","
                + "\"externalPath\":\"/job/Texas/Engineer_" + site.id() + "\","
                + "\"bulletFields\":[\"" + site.id() + "\"]}]}");
        HttpResponse<String> detail = response(200,
                "{\"jobPostingInfo\":{\"jobReqId\":\"different\",\"jobDescription\":\"Wrong job\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);
        when(httpClient.get(any())).thenReturn(detail);

        var result = scraper(site.companyId()).scrape(context(site));

        assertThat(result.errors()).singleElement().asString().contains("job ID does not match");
        assertThat(result.jobs().get(0).descriptionPath()).isNull();
        verify(storage, never()).writeDescription(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void doesNotReportCappedWorkdaySearchAsComplete() throws Exception {
        properties.setMaxSearchPagesPerRun(1);
        HttpResponse<String> search = response(200, "{\"total\":21,\"jobPostings\":[{\"title\":\"Engineer\","
                + "\"externalPath\":\"/job/Texas/Engineer_R123\",\"bulletFields\":[\"R123\"]}]}");
        HttpResponse<String> detail = response(200,
                "{\"jobPostingInfo\":{\"jobReqId\":\"R123\",\"jobDescription\":\"Build systems\"}}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);
        when(httpClient.get(any())).thenReturn(detail);

        var result = scraper("capital-one").scrape(
                new ScrapeContext("run-123", "capital-one", Instant.now()));

        assertThat(result.searchComplete()).isFalse();
    }

    @Test
    void rejectsNegativeWorkdayTotal() throws Exception {
        HttpResponse<String> search = response(200, "{\"total\":-1,\"jobPostings\":[]}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);

        var result = scraper("capital-one").scrape(
                new ScrapeContext("run-123", "capital-one", Instant.now()));

        assertThat(result.searchComplete()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("Invalid Workday search response");
    }

    @Test
    void incrementalRunSkipsUnchangedDetailRequests() throws Exception {
        HttpResponse<String> search = response(200, "{\"total\":1,\"jobPostings\":[{\"title\":\"Engineer\","
                + "\"externalPath\":\"/job/Texas/Engineer_R123\",\"bulletFields\":[\"R123\"]}]}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);
        var context = new ScrapeContext("run-123", "capital-one", Instant.now(), false,
                (job, reliableDate) -> false);

        var result = scraper("capital-one").scrape(context);

        assertThat(result.searchComplete()).isTrue();
        assertThat(result.fullDetailRefresh()).isFalse();
        assertThat(result.detailPagesFetched()).isZero();
        assertThat(result.jobs()).hasSize(1);
        verify(httpClient, never()).get(any());
    }

    @Test
    void appliesConfiguredHumanaFacets() throws Exception {
        List<String> facets = List.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        properties.setHumanaJobFamilyFacetIds(facets);
        HttpResponse<String> search = response(200, "{\"total\":0,\"jobPostings\":[]}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);

        var result = scraper("humana").scrape(new ScrapeContext("run-123", "humana", Instant.now()));

        assertThat(result.errors()).isEmpty();
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(any(), request.capture());
        assertThat(mapper.readTree(request.getValue()).path("appliedFacets"))
                .isEqualTo(mapper.valueToTree(Map.of("jobFamilyGroup", facets)));
    }

    @Test
    void appliesConfiguredCapitalOneFacets() throws Exception {
        String worker = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String time = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        List<String> locations = List.of("cccccccccccccccccccccccccccccccc");
        List<String> families = List.of("dddddddddddddddddddddddddddddddd");
        properties.setCapitalOneWorkerSubtypeFacetId(worker);
        properties.setCapitalOneTimeTypeFacetId(time);
        properties.setCapitalOneLocationFacetIds(locations);
        properties.setCapitalOneJobFamilyFacetIds(families);
        HttpResponse<String> search = response(200, "{\"total\":0,\"jobPostings\":[]}");
        when(httpClient.postJson(any(), anyString())).thenReturn(search);

        var result = scraper("capital-one").scrape(new ScrapeContext("run-123", "capital-one", Instant.now()));

        assertThat(result.errors()).isEmpty();
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(any(), request.capture());
        assertThat(mapper.readTree(request.getValue()).path("appliedFacets"))
                .isEqualTo(mapper.valueToTree(Map.of("workerSubType", List.of(worker), "timeType", List.of(time),
                        "locations", locations, "jobFamilyGroup", families)));
    }

    @Test
    void rejectsInvalidFacetsBeforeMakingRequests() throws Exception {
        List<InvalidFacet> cases = List.of(
                new InvalidFacet("frost-bank", p -> p.setFrostBankJobFamilyFacetId("bad")),
                new InvalidFacet("frost-bank", p -> p.setFrostBankLocationFacetIds(List.of())),
                new InvalidFacet("frost-bank", p -> p.setFrostBankLocationFacetIds(List.of("bad"))),
                new InvalidFacet("citi", p -> p.setCitiJobFamilyFacetId("bad")),
                new InvalidFacet("citi", p -> p.setCitiWorkerSubtypeFacetId("bad")),
                new InvalidFacet("citi", p -> p.setCitiCountryFacetId("bad")),
                new InvalidFacet("citi", p -> p.setCitiStateFacetId("bad")),
                new InvalidFacet("fannie-mae", p -> p.setFannieMaeRemoteTypeFacetId("bad")),
                new InvalidFacet("fannie-mae", p -> p.setFannieMaeJobFamilyFacetId("bad")),
                new InvalidFacet("fannie-mae", p -> p.setFannieMaeLocationFacetId("bad")),
                new InvalidFacet("humana", p -> p.setHumanaJobFamilyFacetIds(null)),
                new InvalidFacet("humana", p -> p.setHumanaJobFamilyFacetIds(List.of())),
                new InvalidFacet("humana", p -> p.setHumanaJobFamilyFacetIds(List.of("bad"))),
                new InvalidFacet("capital-one", p -> p.setCapitalOneWorkerSubtypeFacetId("bad")),
                new InvalidFacet("capital-one", p -> p.setCapitalOneTimeTypeFacetId("bad")),
                new InvalidFacet("capital-one", p -> p.setCapitalOneLocationFacetIds(null)),
                new InvalidFacet("capital-one", p -> p.setCapitalOneLocationFacetIds(List.of())),
                new InvalidFacet("capital-one", p -> p.setCapitalOneLocationFacetIds(List.of("bad"))),
                new InvalidFacet("capital-one", p -> p.setCapitalOneJobFamilyFacetIds(null)),
                new InvalidFacet("capital-one", p -> p.setCapitalOneJobFamilyFacetIds(List.of())),
                new InvalidFacet("capital-one", p -> p.setCapitalOneJobFamilyFacetIds(List.of("bad"))));
        for (var testCase : cases) {
            properties = new JobScraperProperties();
            properties.setRequestDelay(Duration.ZERO);
            testCase.configure().accept(properties);
            var result = scraper(testCase.companyId()).scrape(
                    new ScrapeContext("run-123", testCase.companyId(), Instant.now()));
            assertThat(result.errors()).singleElement().asString().contains("facet IDs");
            assertThat(result.jobs()).isEmpty();
        }
        verify(httpClient, never()).postJson(any(), anyString());
    }

    @Test
    void registersAllScrapersAsComponents() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScanConfiguration.class)
                .withBean(JobSearchHttpClient.class, () -> httpClient)
                .withBean(RawScrapeStorage.class, () -> storage)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JobScraperProperties.class, JobScraperProperties::new)
                .run(context -> {
                    var registry = context.getBean(CompanyScraperRegistry.class);
                    assertThat(registry.find("frost-bank")).containsSame(context.getBean(FrostBankScraper.class));
                    assertThat(registry.find("citi")).containsSame(context.getBean(CitiScraper.class));
                    assertThat(registry.find("fannie-mae")).containsSame(context.getBean(FannieMaeScraper.class));
                    assertThat(registry.find("humana")).containsSame(context.getBean(HumanaScraper.class));
                    assertThat(registry.find("capital-one")).containsSame(context.getBean(CapitalOneScraper.class));
                });
    }

    private WorkdayScraper scraper(String company) {
        return switch (company) {
            case "frost-bank" -> new FrostBankScraper(httpClient, storage, mapper, properties);
            case "citi" -> new CitiScraper(httpClient, storage, mapper, properties);
            case "fannie-mae" -> new FannieMaeScraper(httpClient, storage, mapper, properties);
            case "humana" -> new HumanaScraper(httpClient, storage, mapper, properties);
            case "capital-one" -> new CapitalOneScraper(httpClient, storage, mapper, properties);
            default -> throw new IllegalArgumentException("Unexpected company: " + company);
        };
    }

    private static Stream<Site> sites() {
        return Stream.of(
                new Site("frost-bank", "R262223",
                        "https://frostbank.wd5.myworkdayjobs.com/wday/cxs/frostbank/External",
                        "https://frostbank.wd5.myworkdayjobs.com/External",
                        Map.of("jobFamily", List.of("3a9cae9f1324015f492308c79dc96d35"),
                                "locationHierarchy1", List.of("8fe73009103a424c89e0c6c010a6d8f3",
                                        "58c99fe2f997418f8c54b3ebcf1b6a5d"))),
                new Site("citi", "261011512",
                        "https://citi.wd5.myworkdayjobs.com/wday/cxs/citi/2",
                        "https://citi.wd5.myworkdayjobs.com/2",
                        Map.of("jobFamilyGroup", List.of("e32326e1708d01575bddff0c120102c1"),
                                "workerSubType", List.of("e32326e1708d01289a50257249019cdd"),
                                "Country_and_Jurisdiction", List.of("bc33aa3152ec42d4995f4791a106ed09"),
                                "State_or_Province", List.of("fc77e3a1ab36487f9646d14f7242dd77"))),
                new Site("fannie-mae", "JR3006",
                        "https://fanniemae.wd1.myworkdayjobs.com/wday/cxs/fanniemae/FannieMaeCareers",
                        "https://fanniemae.wd1.myworkdayjobs.com/FannieMaeCareers",
                        Map.of("remoteType", List.of("bf1dc34adb121001727a83cb88650000"),
                                "jobFamilyGroup", List.of("bf1dc34adb12100175ce6a6921f70000"),
                                "locations", List.of("464222325cc3100210ee7e4b71320000"))),
                new Site("humana", "R-436474",
                        "https://humana.wd5.myworkdayjobs.com/wday/cxs/humana/Humana_External_Career_Site",
                        "https://humana.wd5.myworkdayjobs.com/Humana_External_Career_Site",
                        Map.of("jobFamilyGroup", List.of(
                                "fbb60995c99901ebd9a7d74915c586c6",
                                "fbb60995c999011d23ea024a15c58ec6",
                                "fbb60995c999019d8a70164a15c592c6",
                                "fbb60995c99901392fb3e14915c588c6",
                                "fbb60995c99901ad81e1cd4915c584c6",
                                "fbb60995c99901e15cebc24915c582c6",
                                "fbb60995c99901de12ced94a15c5bac6",
                                "fbb60995c999015ce05ca94715c51ac6",
                                "fbb60995c9990180a257d34715c522c6",
                                "fbb60995c99901271b96764715c510c6"))),
                new Site("capital-one", "R1006542",
                        "https://capitalone.wd12.myworkdayjobs.com/wday/cxs/capitalone/Capital_One",
                        "https://capitalone.wd12.myworkdayjobs.com/Capital_One",
                        Map.of("workerSubType", List.of("a12c70bf789e10572aab8e8909a619ae"),
                                "timeType", List.of("2ed180e199081055c65d9d6853aa022d"),
                                "locations", List.of(
                                        "5303cb0ddb47102d5fa3abc17b27051d",
                                        "5303cb0ddb47102d5fa459dc37b705c5",
                                        "af51744fb4fc010ca9b81af56b6b0000",
                                        "e1c37850645e101ce070e09523230000"),
                                "jobFamilyGroup", List.of(
                                        "797094e02d441000c3f4fece96e10000",
                                        "a3ffb53edaa61000c3f5dc76643a0000",
                                        "23c40d87cd84100035bd9201b2bf0000",
                                        "c456977f3e611000c3e1be4b227f0000",
                                        "23c40d87cd84100035bd7d9296540000",
                                        "a12c70bf789e105802e9e79458dc29ab",
                                        "797094e02d441000c3f51c4997090000",
                                        "eaacc21c45691000c3e9ee9790cc0000"))));
    }

    private static ScrapeContext context(Site site) {
        return new ScrapeContext("run-123", site.companyId(), Instant.now());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({FrostBankScraper.class, CitiScraper.class, FannieMaeScraper.class, HumanaScraper.class,
            CapitalOneScraper.class, CompanyScraperRegistry.class})
    static class ScanConfiguration {
    }

    private record Site(String companyId, String id, String apiBase, String publicBase,
                        Map<String, List<String>> facets) {
    }

    private record InvalidFacet(String companyId, Consumer<JobScraperProperties> configure) {
    }
}
