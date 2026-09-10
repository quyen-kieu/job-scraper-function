package com.jobscraper.domain.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    @Test
    void shouldProduceStableHashForSameInput() {
        String hash1 = ContentHasher.sha256("Lead Data Architect|Irving, TX|2026-08-14");
        String hash2 = ContentHasher.sha256("Lead Data Architect|Irving, TX|2026-08-14");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldPrefixHashWithAlgorithm() {
        String hash = ContentHasher.sha256("some content");

        assertThat(hash).startsWith("sha256:");
    }

    @Test
    void shouldProduceDifferentHashesForDifferentInput() {
        String hash1 = ContentHasher.sha256("content A");
        String hash2 = ContentHasher.sha256("content B");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}

