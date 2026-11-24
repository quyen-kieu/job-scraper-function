package com.jobscraper.infrastructure.file;

import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.infrastructure.storage.DescriptionStoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileRawScrapeStorageTest {
    @TempDir
    Path root;

    @Test
    void roundTripsUtf8AndRejectsMismatchedOrEscapingPaths() throws Exception {
        RawStorageProperties properties = new RawStorageProperties();
        properties.setFileRootPath(root.toString());
        LocalFileRawScrapeStorage storage = new LocalFileRawScrapeStorage(properties);
        String path = storage.writeDescription("mckesson", "run-1", "JR/100", "Caf\u00e9 \u2014 engineer");

        assertThat(storage.readDescription("mckesson", "JR/100", path)).isEqualTo("Caf\u00e9 \u2014 engineer");
        assertThat(Path.of(path).getFileName().toString()).doesNotContain("JR/100", ":");
        assertThatThrownBy(() -> storage.readDescription("mckesson", "different", path))
                .isInstanceOf(IllegalArgumentException.class);
        Path outside = Files.createDirectories(root.resolve("outside")).resolve(Path.of(path).getFileName());
        Files.writeString(outside, "unrelated");
        assertThatThrownBy(() -> storage.readDescription("mckesson", "JR/100", outside.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.writeDescription("../other", "run-1", "1", "text"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.writeDescription("mckesson", "../other", "1", "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsDifferentDescriptionsForRetriesAndReadsLegacyPaths() throws Exception {
        RawStorageProperties properties = new RawStorageProperties();
        properties.setFileRootPath(root.toString());
        LocalFileRawScrapeStorage storage = new LocalFileRawScrapeStorage(properties);
        String original = storage.writeDescription("mckesson", "run-1", "JR1", "<p>Original</p>");
        String updated = storage.writeDescription("mckesson", "run-1", "JR1", "Updated plain text");

        assertThat(original).isNotEqualTo(updated);
        assertThat(storage.readDescription("mckesson", "JR1", original)).isEqualTo("<p>Original</p>");
        assertThat(storage.readDescription("mckesson", "JR1", updated)).isEqualTo("Updated plain text");
        Path legacy = Path.of(original).getParent().resolve(DescriptionStoragePaths.fileName("JR1"));
        Files.writeString(legacy, "Legacy text");
        assertThat(storage.readDescription("mckesson", "JR1", legacy.toString())).isEqualTo("Legacy text");
        Files.writeString(Path.of(original), "Unexpected content");
        assertThatThrownBy(() -> storage.readDescription("mckesson", "JR1", original))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
    }
}
