package com.jobscraper.scraper.api;

import java.net.http.HttpResponse;
import java.net.URI;

/**
 * Contract for HTTP operations used during scraping.
 *
 * <p>Abstraction to enable testing of scraper logic without live network calls.</p>
 */
public interface JobSearchHttpClient {

    /**
     * Performs an HTTP GET request.
     *
     * @param uri the URI to fetch
     * @return the HTTP response with response body as a string
     * @throws Exception any network or HTTP error
     */
    HttpResponse<String> get(URI uri) throws Exception;
}

