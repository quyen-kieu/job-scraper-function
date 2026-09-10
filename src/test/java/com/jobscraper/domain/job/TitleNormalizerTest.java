package com.jobscraper.domain.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    @Test
    void shouldStripParentheticalSuffix() {
        String normalized = TitleNormalizer.normalize("Data Architect (Healthcare)");

        assertThat(normalized).isEqualTo("Data Architect");
    }

    @Test
    void shouldStripLevelKeyword() {
        String normalized = TitleNormalizer.normalize("Senior Data Architect");

        assertThat(normalized).isEqualTo("Data Architect");
    }

    @Test
    void shouldFallBackToOriginalWhenResultWouldBeBlank() {
        String normalized = TitleNormalizer.normalize("Senior");

        assertThat(normalized).isEqualTo("Senior");
    }
}

