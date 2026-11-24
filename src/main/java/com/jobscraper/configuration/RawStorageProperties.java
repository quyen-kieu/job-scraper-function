package com.jobscraper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Configuration properties selecting where raw scrape output is written.
 *
 * <p>Supports local-file storage for development and Cosmos DB description handoffs. Exactly
 * one {@link com.jobscraper.infrastructure.storage.RawScrapeStorage} bean is active at a time,
 * chosen by {@code raw-storage.mode}:</p>
 *
 * <ul>
 *   <li>{@code file} &mdash; writes to the local filesystem under {@link #fileRootPath()}</li>
 *   <li>{@code cosmos} &mdash; stores temporary descriptions in Cosmos DB without archiving raw responses</li>
 *   <li>anything else / unset &mdash; no-op (mock paths only, no I/O)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "raw-storage")
public class RawStorageProperties {

    private String mode = "";
    private String fileRootPath = "./output/raw";

    public String mode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = Objects.requireNonNullElse(mode, "");
    }

    public String fileRootPath() {
        return fileRootPath;
    }

    public void setFileRootPath(String fileRootPath) {
        this.fileRootPath = Objects.requireNonNullElse(fileRootPath, "./output/raw");
    }
}

