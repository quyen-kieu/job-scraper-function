package com.jobscraper.infrastructure.blob;

/**
 * Contract for storing raw scrape responses in Blob Storage.
 *
 * <p>Implementations write search pages and job detail responses to Azure Blob Storage
 * with a standard layout: {@code raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/...}</p>
 */
public interface RawScrapeStorage {

    /**
     * Writes a search page response to Blob Storage.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param pageNumber the page number (e.g., 1, 2, 3)
     * @param json the raw JSON response
     * @return the full blob path where the file was written
     * @throws Exception if write fails
     */
    String writeSearchPage(String companyId, String runId, int pageNumber, String json) throws Exception;

    /**
     * Writes a job detail page response to Blob Storage.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param externalJobId the external job ID from the company site
     * @param html the raw HTML response
     * @return the full blob path where the file was written
     * @throws Exception if write fails
     */
    String writeJobDetail(String companyId, String runId, String externalJobId, String html) throws Exception;

    /**
     * Writes a run-summary document to Blob Storage under the {@code normalized/} prefix.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param json the raw JSON run-summary content
     * @return the full blob path where the file was written
     * @throws Exception if write fails
     */
    String writeRunSummary(String companyId, String runId, String json) throws Exception;
}

