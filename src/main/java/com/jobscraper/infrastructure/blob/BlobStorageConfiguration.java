package com.jobscraper.infrastructure.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.jobscraper.configuration.BlobStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Azure Blob Storage dependencies.
 *
 * <p>Beans are only created when {@code azure.storage.enabled=true}.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "azure.storage", name = "enabled", havingValue = "true")
public class BlobStorageConfiguration {

    /**
     * Creates a client for the configured Blob Storage container.
     *
     * <p>Connection string is resolved from {@code azure.storage.connectionString},
     * never hardcoded. Container name is resolved from {@code azure.storage.containerName}.</p>
     *
     * @param properties the blob storage configuration
     * @return a {@link BlobContainerClient} for the configured container
     */
    @Bean
    public BlobContainerClient blobContainerClient(BlobStorageProperties properties) {
        return new BlobServiceClientBuilder()
                .connectionString(properties.connectionString())
                .buildClient()
                .getBlobContainerClient(properties.containerName());
    }
}

