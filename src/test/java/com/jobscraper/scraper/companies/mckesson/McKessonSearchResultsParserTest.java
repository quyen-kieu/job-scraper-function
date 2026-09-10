package com.jobscraper.scraper.companies.mckesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class McKessonSearchResultsParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldParsePaginationMetadataAndJobsFromRealEnvelopeShape() throws Exception {
        String json = loadFixture("search-results-page1.json");
        McKessonSearchResponse envelope = objectMapper.readValue(json, McKessonSearchResponse.class);

        assertThat(envelope.hasJobs()).isTrue();
        assertThat(envelope.hasContent()).isTrue();

        McKessonSearchPage page = McKessonSearchResultsParser.parse(envelope.results());

        assertThat(page.totalJobResults()).isEqualTo(42);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.recordsPerPage()).isEqualTo(15);
        assertThat(page.jobs()).hasSize(2);
    }

    @Test
    void shouldExtractJobFieldsFromEachListItem() throws Exception {
        String json = loadFixture("search-results-page1.json");
        McKessonSearchResponse envelope = objectMapper.readValue(json, McKessonSearchResponse.class);
        McKessonSearchPage page = McKessonSearchResultsParser.parse(envelope.results());

        McKessonJobSummary first = page.jobs().get(0);
        assertThat(first.externalJobId()).isEqualTo("99215825472");
        assertThat(first.titleRaw()).isEqualTo("Lead Data Architect (Healthcare)");
        assertThat(first.location()).isEqualTo("Irving, TX");
        assertThat(first.datePostedRaw()).isEqualTo("08/14/2026");
        assertThat(first.detailPath()).isEqualTo("/en/job/irving/lead-data-architect-healthcare/733/99215825472");

        McKessonJobSummary second = page.jobs().get(1);
        assertThat(second.externalJobId()).isEqualTo("99215825473");
        assertThat(second.titleRaw()).isEqualTo("Senior Software Engineer");
    }

    @Test
    void shouldReturnEmptyPageForBlankResultsFragment() throws Exception {
        String json = loadFixture("search-results-empty-page.json");
        McKessonSearchResponse envelope = objectMapper.readValue(json, McKessonSearchResponse.class);

        McKessonSearchPage page = McKessonSearchResultsParser.parse(envelope.results());

        assertThat(page.jobs()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldReturnEmptyPageForNullResults() {
        McKessonSearchPage page = McKessonSearchResultsParser.parse(null);

        assertThat(page.jobs()).isEmpty();
        assertThat(page.totalJobResults()).isZero();
    }

    private String loadFixture(String filename) throws Exception {
        return Files.readString(Paths.get("src/test/resources/fixtures/mckesson/" + filename));
    }
}

