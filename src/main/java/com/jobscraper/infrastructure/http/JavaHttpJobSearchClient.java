package com.jobscraper.infrastructure.http;

import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Implementation of {@link JobSearchHttpClient} using the JDK's {@link HttpClient}.
 *
 * <p>Honors {@link JobScraperProperties} for timeouts, retries, and user agent headers.</p>
 */
@Component
public class JavaHttpJobSearchClient implements JobSearchHttpClient {

    private final HttpClient httpClient;
    private final JobScraperProperties properties;

    public JavaHttpJobSearchClient(JobScraperProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
    }

    @Override
    public HttpResponse<String> get(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(properties.requestTimeout())
                .header("User-Agent", properties.userAgent())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

