
package com.jobscraper.scraper.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryRangeExtractorTest {

    @Test
    void shouldExtractLabelledBasePayRangeFromMcKessonDescription() {
        String html = """
                <html><body>
                  <section id="ajd-header"><h2 class="job-description__job-title">Architect</h2></section>
                  <div class="ats-description">
                    <p>We are proud to offer a competitive compensation package. In addition to base pay,
                    an annual bonus of $5,000 - $10,000 may be offered.</p>
                    <p><b>Our Base Pay Range for this position</b></p><p></p>$196,100 - $326,900<p></p>
                  </div>
                </body></html>
                """;

        assertThat(SalaryRangeExtractor.extract(html)).contains("$196,100 - $326,900");
    }

    @Test
    void shouldIgnoreSalaryJsonLdInScriptTags() {
        String html = """
                <html><head><script type="application/ld+json">{"description":"$1 - $2"}</script></head>
                <body><div class="ats-description"><p>No pay listed.</p></div></body></html>
                """;

        assertThat(SalaryRangeExtractor.extract(html)).isEmpty();
    }

    @Test
    void shouldFallBackToFirstUnlabelledRange() {
        assertThat(SalaryRangeExtractor.extractFromText("Pay: $101,000 \u2013 $168,400 per year, plus benefits"))
                .contains("$101,000 \u2013 $168,400 per year");
    }

    @Test
    void shouldExtractHourlyRateAndSkipShiftDifferentialAndIncentive() {
        String text = "Shift: Day. $17.40 per hour, Overnight $1.50/hr. Top performers earn up to about "
                + "$1.75/hour in incentive pay.";

        assertThat(SalaryRangeExtractor.extractFromText(text)).contains("$17.40 per hour");
    }

    @Test
    void shouldSkipIncentiveRateWhenItAppearsFirst() {
        String text = "Earn up to about $1.75/hour in incentive pay. Starting pay $22.25/hr.";

        assertThat(SalaryRangeExtractor.extractFromText(text)).contains("$22.25/hr");
    }

    @Test
    void shouldReturnEmptyWhenNoPayIsListed() {
        assertThat(SalaryRangeExtractor.extractFromText("We offer a competitive compensation package.")).isEmpty();
        assertThat(SalaryRangeExtractor.extract(null)).isEmpty();
        assertThat(SalaryRangeExtractor.extract("")).isEmpty();
    }
}

