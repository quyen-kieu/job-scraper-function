package com.jobscraper.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobObservationTest {

    @Test
    void shouldBuildDeterministicObservationIdPerScrapeDate() {
        String id = JobObservation.buildId("mckesson", LocalDate.of(2026, 8, 16), "99215825472");

        assertThat(id).isEqualTo("mckesson:2026-08-16:99215825472");
    }

    @Test
    void shouldProduceDifferentIdsForDifferentScrapeDates() {
        String day1 = JobObservation.buildId("mckesson", LocalDate.of(2026, 8, 16), "99215825472");
        String day2 = JobObservation.buildId("mckesson", LocalDate.of(2026, 8, 17), "99215825472");

        assertThat(day1).isNotEqualTo(day2);
    }

    @Test
    void shouldRejectBlankExternalJobId() {
        assertThatThrownBy(() -> new JobObservation(
                "id", "mckesson", "", "run-1", Instant.now(), LocalDate.now(),
                LocalDate.now(), "normalized", "Lead", "sha256:abc", 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalJobId");
    }
}

