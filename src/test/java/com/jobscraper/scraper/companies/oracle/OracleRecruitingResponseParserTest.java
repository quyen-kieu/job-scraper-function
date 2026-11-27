package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleRecruitingResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseRealSearchResultsPage() throws Exception {
        OracleSearchPage page = OracleRecruitingResponseParser.parseSearchPage(
                objectMapper, loadFixture("search-results-page1.json"));

        assertThat(page.totalJobsCount()).isEqualTo(53);
        assertThat(page.requisitions()).extracting(OracleRequisitionSummary::id)
                .containsExactly("261197", "261186", "261150");
        OracleRequisitionSummary first = page.requisitions().get(0);
        assertThat(first.title()).isEqualTo("Intern - Data Engineer");
        assertThat(first.location()).isEqualTo("Arlington, TX, United States");
        assertThat(first.postedDateRaw()).isEqualTo("2026-10-29");
    }

    @Test
    void shouldRejectSearchResponseWhenItemsAreMissing() {
        assertThatThrownBy(() -> OracleRecruitingResponseParser.parseSearchPage(objectMapper, "{\"items\":[]}"))
                .hasMessageContaining("Invalid Oracle search response");
    }

    @Test
    void shouldRejectMissingCountAndIncompleteRequisitions() {
        assertThatThrownBy(() -> OracleRecruitingResponseParser.parseSearchPage(
                objectMapper, "{\"items\":[{\"requisitionList\":[]}]}"))
                .hasMessageContaining("Invalid Oracle search response");
        assertThatThrownBy(() -> OracleRecruitingResponseParser.parseSearchPage(
                objectMapper, "{\"items\":[{\"TotalJobsCount\":1,\"requisitionList\":[{\"Title\":\"Engineer\"}]}]}"))
                .hasMessageContaining("missing an ID or title");
    }

    @Test
    void shouldAcceptExplicitEmptyResults() throws Exception {
        OracleSearchPage page = OracleRecruitingResponseParser.parseSearchPage(objectMapper,
                "{\"items\":[{\"TotalJobsCount\":0,\"requisitionList\":[]}]}");
        assertThat(page.totalJobsCount()).isZero();
        assertThat(page.requisitions()).isEmpty();
    }

    @Test
    void shouldExtractSalaryRangeFromRealDetailDescription() throws Exception {
        String text = OracleRecruitingResponseParser.parseDescriptionText(
                objectMapper, loadFixture("job-detail-with-salary.json"));

        assertThat(text).doesNotContain("<");
        assertThat(SalaryRangeExtractor.extractFromText(text)).contains("$115,000-$213,000");
    }

    @Test
    void shouldFindNoSalaryWhenDescriptionOnlySaysCompetitivePay() throws Exception {
        String text = OracleRecruitingResponseParser.parseDescriptionText(
                objectMapper, loadFixture("job-detail-without-salary.json"));

        assertThat(text).contains("Competitive pay");
        assertThat(SalaryRangeExtractor.extractFromText(text)).isEmpty();
    }

    @Test
    void shouldPreserveHtmlFragmentsAndEscapePlainSectionsInMixedSources() throws Exception {
        String source = OracleRecruitingResponseParser.parseDescriptionSource(objectMapper, """
                {"items":[{"ExternalDescriptionStr":"<p>Build <b>systems</b></p>",
                 "ExternalResponsibilitiesStr":"Work with A & B",
                 "ExternalQualificationsStr":"<ul><li>Java</li></ul>"}]}
                """);
        assertThat(source).contains("<p>Build <b>systems</b></p>", "Work with A &amp; B",
                "<ul><li>Java</li></ul>");
        assertThat(com.jobscraper.scraper.api.JobDescriptionText.fromHtml(source))
                .contains("Build systems", "Work with A & B", "Java");
    }

    @Test
    void shouldKeepPlainOracleSectionsPlainWhenNoHtmlExists() throws Exception {
        String source = OracleRecruitingResponseParser.parseDescriptionSource(objectMapper, """
                {"items":[{"ExternalDescriptionStr":"Build systems",
                 "ExternalResponsibilitiesStr":"Work with A & B"}]}
                """);
        assertThat(source).isEqualTo("Build systems\n\nWork with A & B");
        assertThat(com.jobscraper.scraper.api.JobDescriptionText.sourceFormat(source))
                .isEqualTo(com.jobscraper.domain.job.DescriptionFormat.PLAIN_TEXT);
    }

    static String loadFixture(String filename) throws Exception {
        return Files.readString(Paths.get("src/test/resources/fixtures/gmfinancial/" + filename));
    }
}
