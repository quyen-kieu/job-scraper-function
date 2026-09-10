package com.jobscraper.domain.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobLevelClassifierTest {

    @Test
    void shouldClassifyLeadFromTitle() {
        String level = JobLevelClassifier.classify("Lead Data Architect (Healthcare)");

        assertThat(level).isEqualTo("Lead");
    }

    @Test
    void shouldDefaultToMidLevelWhenNoKeywordMatches() {
        String level = JobLevelClassifier.classify("Data Architect");

        assertThat(level).isEqualTo("Mid-Level");
    }

    @Test
    void shouldPreferMoreSeniorKeywordWhenMultiplePresent() {
        String level = JobLevelClassifier.classify("Principal Staff Engineer");

        assertThat(level).isEqualTo("Principal");
    }
}

