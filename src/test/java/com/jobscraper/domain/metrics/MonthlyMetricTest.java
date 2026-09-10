package com.jobscraper.domain.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyMetricTest {

    @Test
    void shouldBuildSlugifiedId() {
        String id = MonthlyMetric.buildId("mckesson", "2026-08", "Data Architect", "Lead");

        assertThat(id).isEqualTo("mckesson-2026-08-data-architect-lead");
    }

    @Test
    void shouldComputePostingCountFromObservationSet() {
        MonthlyMetric metric = new MonthlyMetric(
                "id", "mckesson", "2026-08", "Data Architect", "Lead",
                Set.of("9921:2026-08-15", "9922:2026-08-15"), Instant.now());

        assertThat(metric.postingCount()).isEqualTo(2);
    }

    @Test
    void shouldComputeUniquePostingCountFromDistinctJobIds() {
        MonthlyMetric metric = new MonthlyMetric(
                "id", "mckesson", "2026-08", "Data Architect", "Lead",
                Set.of("9921:2026-08-15", "9921:2026-08-16", "9922:2026-08-16"), Instant.now());

        assertThat(metric.uniquePostingCount()).isEqualTo(2);
    }

    @Test
    void shouldTreatDuplicateObservationKeyAsNoOp() {
        Set<String> keys = new HashSet<>();
        keys.add(MonthlyMetric.observationKey("9921", LocalDate.of(2026, 8, 16)));
        keys.add(MonthlyMetric.observationKey("9921", LocalDate.of(2026, 8, 16)));

        MonthlyMetric metric = new MonthlyMetric(
                "id", "mckesson", "2026-08", "Data Architect", "Lead", keys, Instant.now());

        assertThat(metric.postingCount()).isEqualTo(1);
    }
}

