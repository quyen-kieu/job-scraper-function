package com.jobscraper.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.DailyScrapeCommandProducer;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnDemandScrapeCommandProducerFunctionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesForAllEnabledCompaniesWhenCompanyIdOmitted() {
        DailyScrapeCommandProducer producer = mock(DailyScrapeCommandProducer.class);
        when(producer.publishDailyCommands()).thenReturn(2);

        OnDemandScrapeCommandProducerFunction function =
                new OnDemandScrapeCommandProducerFunction(producer, objectMapper);

        HttpRequestMessage<Optional<String>> request = fakeRequest(Map.of());
        HttpResponseMessage response = function.run(request, fakeContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody())).contains("\"companiesPublished\":2");
        verify(producer, times(1)).publishDailyCommands();
    }

    @Test
    void publishesSingleCommandWhenCompanyIdProvided() {
        DailyScrapeCommandProducer producer = mock(DailyScrapeCommandProducer.class);
        when(producer.publishCommand("mckesson")).thenReturn(Optional.of("run-123"));

        OnDemandScrapeCommandProducerFunction function =
                new OnDemandScrapeCommandProducerFunction(producer, objectMapper);

        HttpRequestMessage<Optional<String>> request = fakeRequest(Map.of("companyId", "mckesson"));
        HttpResponseMessage response = function.run(request, fakeContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody())).contains("run-123");
        verify(producer, times(1)).publishCommand("mckesson");
    }

    @Test
    void returnsBadRequestForUnknownOrDisabledCompany() {
        DailyScrapeCommandProducer producer = mock(DailyScrapeCommandProducer.class);
        when(producer.publishCommand("oracle")).thenReturn(Optional.empty());

        OnDemandScrapeCommandProducerFunction function =
                new OnDemandScrapeCommandProducerFunction(producer, objectMapper);

        HttpRequestMessage<Optional<String>> request = fakeRequest(Map.of("companyId", "oracle"));
        HttpResponseMessage response = function.run(request, fakeContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(response.getBody())).contains("Unknown or disabled companyId");
    }

    @SuppressWarnings("unchecked")
    private HttpRequestMessage<Optional<String>> fakeRequest(Map<String, String> queryParams) {
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        when(request.getQueryParameters()).thenReturn(new HashMap<>(queryParams));
        when(request.createResponseBuilder(any(HttpStatus.class))).thenAnswer(invocation -> {
            HttpStatus status = invocation.getArgument(0);
            return new FakeResponseBuilder(status);
        });
        return request;
    }

    private ExecutionContext fakeContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        return context;
    }

    private static final class FakeResponseBuilder implements HttpResponseMessage.Builder {
        private final HttpStatus status;
        private Object body;

        private FakeResponseBuilder(HttpStatus status) {
            this.status = status;
        }

        @Override
        public HttpResponseMessage.Builder status(HttpStatusType httpStatusType) {
            return this;
        }

        @Override
        public HttpResponseMessage.Builder header(String key, String value) {
            return this;
        }

        @Override
        public HttpResponseMessage.Builder body(Object body) {
            this.body = body;
            return this;
        }

        @Override
        public HttpResponseMessage build() {
            return new FakeHttpResponseMessage(status, body);
        }
    }

    private static final class FakeHttpResponseMessage implements HttpResponseMessage {
        private final HttpStatus status;
        private final Object body;

        private FakeHttpResponseMessage(HttpStatus status, Object body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public String getHeader(String key) {
            return null;
        }

        @Override
        public Object getBody() {
            return body;
        }
    }
}



