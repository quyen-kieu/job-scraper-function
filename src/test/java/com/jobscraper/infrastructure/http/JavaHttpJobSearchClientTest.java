package com.jobscraper.infrastructure.http;

import com.jobscraper.function.JobScraperProperties;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void shouldPostJsonWithConfiguredHeaders() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs", exchange -> {
            request.set(exchange.getRequestMethod() + "|" + exchange.getRequestHeaders().getFirst("Content-Type")
                    + "|" + exchange.getRequestHeaders().getFirst("User-Agent") + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JobScraperProperties properties = new JobScraperProperties();
            properties.setUserAgent("custom-agent/1.0");
            JavaHttpJobSearchClient client = new JavaHttpJobSearchClient(properties);
            HttpResponse<String> response = client.postJson(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jobs"), "{\"offset\":0}");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{}");
            assertThat(request.get()).isEqualTo("POST|application/json|custom-agent/1.0|{\"offset\":0}");
        } finally {
            server.stop(0);
        }
    }
}
