package com.jobscraper.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizedJobPostingTest {

    @Test
    void shouldBuildDeterministicId() {
        String id = NormalizedJobPosting.buildId("mckesson", "99215825472");

        assertThat(id).isEqualTo("mckesson:99215825472");
    }

    @Test
    void shouldRejectBlankCompanyId() {
        assertThatThrownBy(() -> new NormalizedJobPosting(
                "id", "", "external-id", "raw", "normalized", "Unknown", "Irving, TX",
                LocalDate.now(), Instant.now(), Instant.now(), true, "sha256:abc", 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void shouldConstructValidPosting() {
        Instant now = Instant.now();
        NormalizedJobPosting posting = new NormalizedJobPosting(
                "mckesson:99215825472", "mckesson", "99215825472",
                "Lead Data Architect (Healthcare)", "Data Architect", "Lead",
                "Irving, TX", LocalDate.of(2026, 8, 14), now, now, true,
                "sha256:abc123", 1
        );

        assertThat(posting.id()).isEqualTo("mckesson:99215825472");
        assertThat(posting.isActive()).isTrue();
        assertThat(posting.schemaVersion()).isEqualTo(1);
    }
}

