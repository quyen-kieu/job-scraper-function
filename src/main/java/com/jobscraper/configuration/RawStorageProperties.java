package com.jobscraper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Configuration properties selecting where raw scrape output is written.
 *
 * <p>Supports a local-file destination for development and manual test runs, in addition
 * to the Azure Blob Storage destination selected via {@link BlobStorageProperties}. Exactly
 * one {@link com.jobscraper.infrastructure.blob.RawScrapeStorage} bean is active at a time,
 * chosen by {@code raw-storage.mode}:</p>
 *
 * <ul>
 *   <li>{@code file} &mdash; writes to the local filesystem under {@link #fileRootPath()}</li>
 *   <li>{@code blob} &mdash; writes to Azure Blob Storage (requires {@code azure.storage.enabled=true})</li>
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


