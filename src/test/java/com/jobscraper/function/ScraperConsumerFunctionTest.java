package com.jobscraper.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCommandHandler;
import com.jobscraper.scraper.api.ScrapeResult;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ScraperConsumerFunctionTest {

    @Mock
    private ScrapeCommandHandler commandHandler;

    @Test
    void shouldCreateScraperConsumerFunctionWithDependencies() {
        ObjectMapper objectMapper = new ObjectMapper();
        ScraperConsumerFunction function = new ScraperConsumerFunction(commandHandler, objectMapper);

        assertThat(function).isNotNull();
    }

    @Test
    void shouldSkipMalformedScrapeCommandPayloads() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ScraperConsumerFunction function = new ScraperConsumerFunction(commandHandler, objectMapper);
        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        Mockito.when(context.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));

        function.run("{\"companyId\":\"mckesson\"}", context);
    }

    @Test
    void shouldUnwrapKafkaMetadataEnvelopeBeforeProcessing() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ScraperConsumerFunction function = new ScraperConsumerFunction(commandHandler, objectMapper);
        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        Mockito.when(context.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));

        String wrappedMessage = "{\"Offset\":2,\"Partition\":0,\"Topic\":\"job.scrape.commands\",\"Value\":\"{\\\"runId\\\":\\\"abc\\\",\\\"companyId\\\":\\\"mckesson\\\",\\\"requestedAt\\\":1787565610.305997200}\",\"Key\":\"mckesson\",\"Headers\":[]}";
        Mockito.when(commandHandler.handle(Mockito.anyString()))
                .thenReturn(new ScrapeResult(
                        "abc",
                        "mckesson",
                        Instant.now(),
                        Instant.now(),
                        1,
                        2,
                        List.of(),
                        List.of()));

        function.run(wrappedMessage, context);

        Mockito.verify(commandHandler).handle("{\"runId\":\"abc\",\"companyId\":\"mckesson\",\"requestedAt\":1787565610.305997200}");
    }

    @Test
    void logsRunCorrelatedHybridStatus() throws Exception {
        ScraperConsumerFunction function = new ScraperConsumerFunction(commandHandler, new ObjectMapper());
        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        java.util.logging.Logger logger = Mockito.mock(java.util.logging.Logger.class);
        Mockito.when(context.getLogger()).thenReturn(logger);
        Mockito.when(commandHandler.handle(Mockito.anyString())).thenReturn(new ScrapeResult(
                "run-1", "mckesson", Instant.now(), Instant.now(), 3, 60,
                List.of(), List.of("Search page 4 failed: HTTP 503; private details"), List.of(), false, true));

        function.run("{\"runId\":\"run-1\",\"companyId\":\"mckesson\",\"requestedAt\":\"2026-01-01T00:00:00Z\"}", context);

        Mockito.verify(logger).info(Mockito.contains(
                "Scrape completed for company mckesson: 3 search pages, 60 detail pages runId=run-1 searchComplete=false fullDetailRefresh=true uniqueJobs=0 errors=1"));
        Mockito.verify(logger).warning(Mockito.contains("company mckesson runId=run-1"));
        Mockito.verify(logger).warning("Search failure companyId=mckesson runId=run-1 page=4 reason=failed HTTP 503");
        Mockito.verify(logger, Mockito.never()).warning(Mockito.contains("private details"));
    }
}
