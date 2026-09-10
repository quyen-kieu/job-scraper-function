package com.jobscraper.scraper.api;

/**
 * Contract for a company-specific job scraper implementation.
 *
 * <p>Each company is implemented as a pluggable bean that conforms to this interface.
 * The {@link com.jobscraper.scraper.registry.CompanyScraperRegistry} looks up the matching
 * scraper by company ID when handling a {@code job.scrape.commands} message.</p>
 */
public interface CompanyScraper {

    /**
     * Returns the company ID this scraper handles.
     *
     * @return the company ID (e.g., {@code "mckesson"})
     */
    String companyId();

    /**
     * Executes a scrape for this company.
     *
     * <p>Responsibilities:
     * <ul>
     *   <li>Fetch search and detail pages using an HTTP client</li>
     *   <li>Parse JSON envelopes and embedded HTML</li>
     *   <li>Write raw responses to Blob Storage</li>
     *   <li>Collect Blob paths and error messages</li>
     * </ul>
     *
     * <p>If this method throws an exception, it is propagated out of the Azure Kafka trigger
     * function, which results in no checkpoint and automatic redelivery by the broker.</p>
     *
     * @param context the scrape run context (runId, companyId, requestedAt)
     * @return the result of the scrape operation
     * @throws Exception any exception during scraping (surfaces for redelivery)
     */
    ScrapeResult scrape(ScrapeContext context) throws Exception;
}

