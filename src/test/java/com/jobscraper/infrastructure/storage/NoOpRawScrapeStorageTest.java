package com.jobscraper.infrastructure.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpRawScrapeStorageTest {
    @Test
    void explicitlyRejectsDescriptions() {
        var storage = new NoOpRawScrapeStorage();
        assertThatThrownBy(() -> storage.writeDescription("mckesson", "run-1", "1", "text"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires");
        assertThatThrownBy(() -> storage.readDescription("mckesson", "1", "path"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires");
    }
}
