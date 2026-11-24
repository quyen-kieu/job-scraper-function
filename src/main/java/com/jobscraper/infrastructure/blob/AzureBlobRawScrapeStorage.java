package com.jobscraper.infrastructure.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.jobscraper.configuration.BlobStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link RawScrapeStorage} using Azure Blob Storage.
 *
 * <p>Writes files to the configured container with the standard path layout:
 * {@code raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/search-page-{pageNumber}.json}
 * {@code raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/job-{externalJobId}.html}</p>
 */
@Component
@ConditionalOnProperty(prefix = "azure.storage", name = "enabled", havingValue = "true")
public class AzureBlobRawScrapeStorage implements RawScrapeStorage {

    private final BlobContainerClient containerClient;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public AzureBlobRawScrapeStorage(BlobContainerClient containerClient) {
        this.containerClient = containerClient;
    }

    @Override
    public String writeSearchPage(String companyId, String runId, int pageNumber, String json) throws Exception {
        String blobPath = buildSearchPagePath(companyId, runId, pageNumber);
        byte[] data = json.getBytes();
        containerClient.getBlobClient(blobPath).upload(
                new java.io.ByteArrayInputStream(data),
                data.length,
                true);
        return blobPath;
    }

    @Override
    public String writeJobDetail(String companyId, String runId, String externalJobId, String html) throws Exception {
        String blobPath = buildJobDetailPath(companyId, runId, externalJobId);
        byte[] data = html.getBytes();
        containerClient.getBlobClient(blobPath).upload(
                new java.io.ByteArrayInputStream(data),
                data.length,
                true);
        return blobPath;
    }

    @Override
    public String writeDescription(String companyId, String runId, String externalJobId, String text) {
        DescriptionStoragePaths.validateCompany(companyId);
        DescriptionStoragePaths.validateRunId(runId);
        String path = String.format("raw/%s/%s/%s/%s", companyId, LocalDate.now().format(DATE_FORMATTER),
                runId, DescriptionStoragePaths.fileName(externalJobId));
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        containerClient.getBlobClient(path).upload(new java.io.ByteArrayInputStream(data), data.length, true);
        return path;
    }

    @Override
    public String readDescription(String companyId, String externalJobId, String path) {
        DescriptionStoragePaths.validateCompany(companyId);
        String prefix = "raw/" + companyId + "/";
        String suffix = "/" + DescriptionStoragePaths.fileName(externalJobId);
        if (path == null || !path.startsWith(prefix) || !path.endsWith(suffix)
                || path.contains("..") || path.contains("\\")) {
            throw new IllegalArgumentException("Invalid description blob path");
        }
        return containerClient.getBlobClient(path).downloadContent().toString();
    }

    @Override
    public String writeRunSummary(String companyId, String runId, String json) throws Exception {
        String blobPath = buildRunSummaryPath(companyId, runId);
        byte[] data = json.getBytes();
        containerClient.getBlobClient(blobPath).upload(
                new java.io.ByteArrayInputStream(data),
                data.length,
                true);
        return blobPath;
    }

    private String buildSearchPagePath(String companyId, String runId, int pageNumber) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        return String.format("raw/%s/%s/%s/search-page-%d.json", companyId, datePart, runId, pageNumber);
    }

    private String buildJobDetailPath(String companyId, String runId, String externalJobId) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        return String.format("raw/%s/%s/%s/job-%s.html", companyId, datePart, runId, externalJobId);
    }

    private String buildRunSummaryPath(String companyId, String runId) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        return String.format("normalized/%s/%s/%s/run-summary.json", companyId, datePart, runId);
    }
}
