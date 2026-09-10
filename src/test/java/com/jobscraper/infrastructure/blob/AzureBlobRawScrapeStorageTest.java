package com.jobscraper.infrastructure.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
class AzureBlobRawScrapeStorageTest {

    @Container
    static GenericContainer<?> azurite = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:latest")
            .withExposedPorts(10000);

    private BlobContainerClient containerClient;
    private AzureBlobRawScrapeStorage storage;

    @BeforeEach
    void setUp() {
        String connectionString = String.format(
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXOPFSQbTP1Fb2qYzcS73rFSRv+FWuvxlulLXxocFFCa9Tjzo0N8B0yWo1DjgkY4tac=;BlobEndpoint=http://%s:%d/devstoreaccount1;",
                azurite.getHost(),
                azurite.getFirstMappedPort()
        );

        containerClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .createBlobContainer("test-container");

        storage = new AzureBlobRawScrapeStorage(containerClient);
    }

    @Test
    void shouldWriteSearchPageAndReturnBlobPath() throws Exception {
        String json = "{\"result\": \"success\"}";
        String path = storage.writeSearchPage("mckesson", "run-123", 1, json);

        assertThat(path)
                .contains("raw/mckesson")
                .contains("run-123")
                .contains("search-page-1.json");

        // Verify the file was actually written
        var blob = containerClient.getBlobClient(path);
        assertThat(blob.exists()).isTrue();
        String content = blob.downloadContent().toString();
        assertThat(content).isEqualTo(json);
    }

    @Test
    void shouldWriteJobDetailAndReturnBlobPath() throws Exception {
        String html = "<html><body>Job Details</body></html>";
        String path = storage.writeJobDetail("mckesson", "run-123", "99215825472", html);

        assertThat(path)
                .contains("raw/mckesson")
                .contains("run-123")
                .contains("job-99215825472.html");

        // Verify the file was actually written
        var blob = containerClient.getBlobClient(path);
        assertThat(blob.exists()).isTrue();
        String content = blob.downloadContent().toString();
        assertThat(content).isEqualTo(html);
    }

    @Test
    void shouldWriteRunSummaryUnderNormalizedPrefix() throws Exception {
        String json = "{\"runId\": \"run-123\", \"companiesProcessed\": 1}";
        String path = storage.writeRunSummary("mckesson", "run-123", json);

        assertThat(path)
                .contains("normalized/mckesson")
                .contains("run-123")
                .contains("run-summary.json");

        // Verify the file was actually written
        var blob = containerClient.getBlobClient(path);
        assertThat(blob.exists()).isTrue();
        String content = blob.downloadContent().toString();
        assertThat(content).isEqualTo(json);
    }
}



