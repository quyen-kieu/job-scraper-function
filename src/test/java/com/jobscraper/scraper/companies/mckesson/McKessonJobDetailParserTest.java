package com.jobscraper.scraper.companies.mckesson;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class McKessonJobDetailParserTest {

    @Test
    void shouldParseTitleAndLocationFromRealDetailPageShape() throws Exception {
        String html = loadFixture("job-detail-real.html");

        Optional<McKessonJobDetailParser.McKessonJobDetail> detail = McKessonJobDetailParser.parse(html);

        assertThat(detail).isPresent();
        assertThat(detail.get().titleRaw()).isEqualTo("Lead Data Architect (Healthcare)");
        assertThat(detail.get().location()).isEqualTo("Irving, Texas");
    }

    @Test
    void shouldReturnEmptyWhenHeaderSectionMissing() {
        Optional<McKessonJobDetailParser.McKessonJobDetail> detail =
                McKessonJobDetailParser.parse("<html><body>no header here</body></html>");

        assertThat(detail).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankHtml() {
        assertThat(McKessonJobDetailParser.parse(null)).isEmpty();
        assertThat(McKessonJobDetailParser.parse("")).isEmpty();
    }

    private String loadFixture(String filename) throws Exception {
        return Files.readString(Paths.get("src/test/resources/fixtures/mckesson/" + filename));
    }
}

