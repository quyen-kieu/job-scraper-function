package com.jobscraper.infrastructure.file;

import com.jobscraper.configuration.RawStorageProperties;
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
}
