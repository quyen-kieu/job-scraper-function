package com.jobscraper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Configuration properties for Azure Cosmos DB.
 *
 * <p>All fields are injected from environment variables or Spring configuration,
 * never hardcoded. The {@code key} field holds a secret and must never be logged.</p>
 */
@ConfigurationProperties(prefix = "azure.cosmos")
public class CosmosProperties {

    private boolean enabled = false;
    private String endpoint = "";
    private String key = "";
    private String databaseName = "job-scraper";
    private String postingsContainer = "job-postings";
    private String observationsContainer = "job-observations";
    private String metricsContainer = "monthly-metrics";

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String endpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = Objects.requireNonNullElse(endpoint, "");
    }

    public String key() {
        return key;
    }

    public void setKey(String key) {
        this.key = Objects.requireNonNullElse(key, "");
    }

    public String databaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = Objects.requireNonNullElse(databaseName, "job-scraper");
    }

    public String postingsContainer() {
        return postingsContainer;
    }

    public void setPostingsContainer(String postingsContainer) {
        this.postingsContainer = Objects.requireNonNullElse(postingsContainer, "job-postings");
    }

    public String observationsContainer() {
        return observationsContainer;
    }

    public void setObservationsContainer(String observationsContainer) {
        this.observationsContainer = Objects.requireNonNullElse(observationsContainer, "job-observations");
    }

    public String metricsContainer() {
        return metricsContainer;
    }

    public void setMetricsContainer(String metricsContainer) {
        this.metricsContainer = Objects.requireNonNullElse(metricsContainer, "monthly-metrics");
    }
}

