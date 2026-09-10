package com.jobscraper.infrastructure.http;

import com.jobscraper.function.JobScraperProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class JavaHttpJobSearchClientTest {

    @Test
    void shouldSetConfiguredUserAgentHeader() throws Exception {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setUserAgent("custom-agent/1.0");

        JavaHttpJobSearchClient client = new JavaHttpJobSearchClient(properties);
        assertThat(properties.userAgent()).isEqualTo("custom-agent/1.0");
    }

    @Test
    void shouldApplyConfiguredRequestTimeout() throws Exception {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setRequestTimeout(Duration.ofSeconds(15));

        JavaHttpJobSearchClient client = new JavaHttpJobSearchClient(properties);
        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void shouldUseDefaultUserAgent() {
        JobScraperProperties properties = new JobScraperProperties();
        JavaHttpJobSearchClient client = new JavaHttpJobSearchClient(properties);

        assertThat(properties.userAgent()).isEqualTo("job-scraper/0.1");
    }

    @Test
    void shouldUseDefaultRequestTimeout() {
        JobScraperProperties properties = new JobScraperProperties();
        JavaHttpJobSearchClient client = new JavaHttpJobSearchClient(properties);

        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}

