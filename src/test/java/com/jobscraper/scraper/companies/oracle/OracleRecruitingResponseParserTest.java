package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.scraper.api.SalaryRangeExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldReturnEmptyPageWhenResponseHasNoItems() throws Exception {
        OracleSearchPage page = OracleRecruitingResponseParser.parseSearchPage(objectMapper, "{\"items\":[]}");

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

    static String loadFixture(String filename) throws Exception {
        return Files.readString(Paths.get("src/test/resources/fixtures/gmfinancial/" + filename));
    }
}

