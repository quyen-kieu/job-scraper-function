package com.jobscraper.application.command;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScrapeCompanyCommandTest {

    @Test
    void rejectsBlankRunId() {
        assertThatThrownBy(() -> new ScrapeCompanyCommand(" ", "mckesson", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void rejectsBlankCompanyId() {
        assertThatThrownBy(() -> new ScrapeCompanyCommand("run-1", "", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }
}

