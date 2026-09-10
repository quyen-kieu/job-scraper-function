package com.jobscraper.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapeResult;
import com.jobscraper.scraper.registry.CompanyScraperRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeCommandHandlerTest {

    private ObjectMapper objectMapper;

    @Mock
    private CompanyScraperRegistry registry;

    @Mock
    private CompanyScraper mockScraper;

    @Mock
    private ScrapeCompletedPublisher scrapeCompletedPublisher;

    private ScrapeCommandHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new ScrapeCommandHandler(objectMapper, registry, scrapeCompletedPublisher);
    }

    @Test
    void shouldDelegateToMatchingCompanyScraper() throws Exception {
        String runId = "run-123";
        String companyId = "mckesson";
        Instant now = Instant.now();

        String json = objectMapper.writeValueAsString(
                new ScrapeCompanyCommand(runId, companyId, now)
        );

        ScrapeResult expectedResult = new ScrapeResult(
                runId, companyId, now, now.plusSeconds(30), 1, 5,
                List.of("blob-path-1"), List.of()
        );

        when(registry.find(companyId)).thenReturn(java.util.Optional.of(mockScraper));
        when(mockScraper.scrape(any(ScrapeContext.class))).thenReturn(expectedResult);

        ScrapeResult result = handler.handle(json);

        assertThat(result)
                .isEqualTo(expectedResult);
    }

    @Test
    void shouldPublishScrapeCompletedEventExactlyOnceOnSuccess() throws Exception {
        String runId = "run-123";
        String companyId = "mckesson";
        Instant now = Instant.now();

        String json = objectMapper.writeValueAsString(
                new ScrapeCompanyCommand(runId, companyId, now)
        );

        ScrapeResult expectedResult = new ScrapeResult(
                runId, companyId, now, now.plusSeconds(30), 2, 7,
                List.of("blob-path-1", "blob-path-2"), List.of()
        );

        when(registry.find(companyId)).thenReturn(java.util.Optional.of(mockScraper));
        when(mockScraper.scrape(any(ScrapeContext.class))).thenReturn(expectedResult);

        handler.handle(json);

        ArgumentCaptor<ScrapeCompletedEvent> captor = ArgumentCaptor.forClass(ScrapeCompletedEvent.class);
        verify(scrapeCompletedPublisher, times(1)).publish(captor.capture());

        ScrapeCompletedEvent published = captor.getValue();
        assertThat(published.runId()).isEqualTo(runId);
        assertThat(published.companyId()).isEqualTo(companyId);
        assertThat(published.startedAt()).isEqualTo(expectedResult.startedAt());
        assertThat(published.completedAt()).isEqualTo(expectedResult.completedAt());
        assertThat(published.searchPagesFetched()).isEqualTo(2);
        assertThat(published.detailPagesFetched()).isEqualTo(7);
        assertThat(published.blobPaths()).containsExactly("blob-path-1", "blob-path-2");
        assertThat(published.errors()).isEmpty();
    }

    @Test
    void shouldThrowWhenNoScraperMatchesCompanyId() throws Exception {
        String runId = "run-123";
        String companyId = "unknown-company";
        Instant now = Instant.now();

        String json = objectMapper.writeValueAsString(
                new ScrapeCompanyCommand(runId, companyId, now)
        );

        when(registry.find(companyId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> handler.handle(json))
                .isInstanceOf(ScrapeCommandHandler.UnknownCompanyScraperException.class)
                .hasMessageContaining("No scraper registered for company: " + companyId);

        verify(scrapeCompletedPublisher, never()).publish(any());
    }

    @Test
    void shouldPropagateScraperException() throws Exception {
        String runId = "run-123";
        String companyId = "mckesson";
        Instant now = Instant.now();

        String json = objectMapper.writeValueAsString(
                new ScrapeCompanyCommand(runId, companyId, now)
        );

        when(registry.find(companyId)).thenReturn(java.util.Optional.of(mockScraper));
        when(mockScraper.scrape(any(ScrapeContext.class)))
                .thenThrow(new IllegalStateException("Network error"));

        assertThatThrownBy(() -> handler.handle(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Network error");

        verify(scrapeCompletedPublisher, never()).publish(any());
    }
}

