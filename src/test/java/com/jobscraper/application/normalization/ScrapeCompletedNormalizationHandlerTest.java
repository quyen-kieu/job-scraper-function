package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.notification.RunCompletionAggregator;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeCompletedNormalizationHandlerTest {

    @Mock
    private NormalizedPostingPublisher publisher;

    @Mock
    private RunCompletionAggregator runCompletionAggregator;

    @Mock
    private DailyNotificationPublisher dailyNotificationPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(runCompletionAggregator.registerExpectedCompany(anyString(), anyString(), anyInt(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldPublishOnePostingPerDiscoveredJob() throws Exception {
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 2,
                List.of(
                        "raw/mckesson/2026/08/16/run-123/job-99215825472.html",
                        "raw/mckesson/2026/08/16/run-123/job-99215825473.html",
                        "raw/mckesson/2026/08/16/run-123/search-page-1.json"
                ),
                List.of()
        );

        int count = handler.handle(objectMapper.writeValueAsString(event));

        assertThat(count).isEqualTo(2);
        verify(publisher, times(2)).publish(org.mockito.ArgumentMatchers.any(NormalizedJobPosting.class));
        verify(runCompletionAggregator).registerExpectedCompany("run-123", "mckesson", 2, List.of());
    }

    @Test
    void shouldApplyTitleAndLevelNormalizationRules() throws Exception {
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 1,
                List.of("raw/mckesson/2026/08/16/run-123/job-99215825472.html"),
                List.of()
        );

        handler.handle(objectMapper.writeValueAsString(event));

        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(captor.capture());

        NormalizedJobPosting posting = captor.getValue();
        assertThat(posting.titleNormalized()).isNotBlank();
        assertThat(posting.jobLevel()).isEqualTo("Mid-Level");
        assertThat(posting.id()).isEqualTo("mckesson:99215825472");
    }

    @Test
    void shouldPublishDailyNotificationWhenAggregatorReportsCompletion() throws Exception {
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 0,
                List.of(),
                List.of()
        );

        DailyNotificationRequestedEvent completedEvent = new DailyNotificationRequestedEvent(
                "run-123", LocalDate.of(2026, 8, 16), 1, 0, 0, 0, List.of());
        when(runCompletionAggregator.registerExpectedCompany("run-123", "mckesson", 0, List.of()))
                .thenReturn(Optional.of(completedEvent));

        handler.handle(objectMapper.writeValueAsString(event));

        verify(dailyNotificationPublisher).publish(completedEvent);
    }

    @Test
    void shouldPropagatePublisherFailure() throws Exception {
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 1,
                List.of("raw/mckesson/2026/08/16/run-123/job-99215825472.html"),
                List.of()
        );

        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any(NormalizedJobPosting.class));

        assertThatThrownBy(() -> handler.handle(objectMapper.writeValueAsString(event)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka unavailable");

        verify(runCompletionAggregator, never()).registerExpectedCompany(any(), any(), anyInt(), any());
    }

    @Test
    void shouldIgnoreNonDetailBlobPaths() throws Exception {
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 0,
                List.of("raw/mckesson/2026/08/16/run-123/search-page-1.json"),
                List.of()
        );

        int count = handler.handle(objectMapper.writeValueAsString(event));

        assertThat(count).isZero();
    }

    @Test
    void shouldExtractExternalJobIdFromWindowsAbsolutePathEvenWhenDirectoryNameContainsJobPrefix() throws Exception {
        // Regression test: reproduces a real local run where LocalFileRawScrapeStorage returned
        // an OS-native Windows absolute path whose project directory name ("job-scraper-function")
        // itself contains the literal substring "job-", which previously caused the extraction
        // regex to greedily match from that earlier occurrence instead of the actual file name.
        ScrapeCompletedNormalizationHandler handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        String windowsPath = "C:\\Code\\job-scraper\\job-scraper-function\\target\\azure-functions\\"
                + "job-scraper-function-20260815154131779\\.\\output\\raw\\mckesson\\2026\\09\\08\\"
                + "a26c90f0-9b52-41da-8599-dbc1cd9a4d89\\job-100367770672.html";

        ScrapeCompletedEvent event = new ScrapeCompletedEvent(
                "run-123", "mckesson", Instant.now(), Instant.now(), 1, 1,
                List.of(windowsPath),
                List.of()
        );

        handler.handle(objectMapper.writeValueAsString(event));

        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(captor.capture());

        NormalizedJobPosting posting = captor.getValue();
        assertThat(posting.externalJobId()).isEqualTo("100367770672");
        assertThat(posting.id()).isEqualTo("mckesson:100367770672");
    }
}
