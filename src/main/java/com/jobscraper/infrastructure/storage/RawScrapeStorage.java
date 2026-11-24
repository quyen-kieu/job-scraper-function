package com.jobscraper.infrastructure.storage;

/**
 * Contract for optional raw scrape archives and description handoffs.
 *
 * <p>Production uses Cosmos DB for description handoffs and does not archive raw responses.</p>
 */
public interface RawScrapeStorage {

    /**
     * Writes or discards a search page response.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param pageNumber the page number (e.g., 1, 2, 3)
     * @param json the raw JSON response
     * @return its path or a marker indicating it was not stored
     * @throws Exception if write fails
     */
    String writeSearchPage(String companyId, String runId, int pageNumber, String json) throws Exception;

    /**
     * Writes or discards a job detail response.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param externalJobId the external job ID from the company site
     * @param html the raw HTML response
     * @return its path or a marker indicating it was not stored
     * @throws Exception if write fails
     */
    String writeJobDetail(String companyId, String runId, String externalJobId, String html) throws Exception;

    /**
     * Writes the source description separately so Kafka events carry only its reference and format.
     */
    String writeDescription(String companyId, String runId, String externalJobId, String text) throws Exception;

    /**
     * Reads a description previously written for the specified company and posting.
     */
    String readDescription(String companyId, String externalJobId, String path) throws Exception;

    /**
     * Writes or discards a run summary.
     *
     * @param companyId the company ID for path partitioning
     * @param runId the scrape run ID for path partitioning
     * @param json the raw JSON run-summary content
     * @return its path or a marker indicating it was not stored
     * @throws Exception if write fails
     */
    String writeRunSummary(String companyId, String runId, String json) throws Exception;
}
