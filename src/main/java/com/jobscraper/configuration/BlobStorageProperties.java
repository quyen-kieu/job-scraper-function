package com.jobscraper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Configuration properties for Azure Blob Storage.
 *
 * <p>All fields are injected from environment variables or Spring configuration,
 * never hardcoded. Intended for wiring Spring beans that access Blob Storage.</p>
 */
@ConfigurationProperties(prefix = "azure.storage")
public class BlobStorageProperties {

    private boolean enabled = false;
    private String connectionString = "";
    private String containerName = "";

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String connectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = Objects.requireNonNullElse(connectionString, "");
    }

    public String containerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = Objects.requireNonNullElse(containerName, "");
    }
}

