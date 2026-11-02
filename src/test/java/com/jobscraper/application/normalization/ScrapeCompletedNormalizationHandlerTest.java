package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.notification.NotificationPosting;
import com.jobscraper.application.persistence.PersistenceOutcome;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.scraper.api.ScrapedJob;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeCompletedNormalizationHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Mock
    private NormalizedPostingPublisher publisher;

    @Mock
    private JobPostingRepository postingRepository;

    @Mock
    private DailyNotificationPublisher dailyNotificationPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ScrapeCompletedNormalizationHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(postingRepository.findById(anyString(), anyString())).thenReturn(Optional.empty());
        handler = new ScrapeCompletedNormalizationHandler(
                objectMapper, publisher, postingRepository, dailyNotificationPublisher, fixedClock);
    }

    @Test
    void shouldBuildPostingsFromScrapedJobsWithRealTitleLocationAndUrl() throws Exception {
        ScrapedJob job = new ScrapedJob("99215825472", "Lead Data Architect (Healthcare)", "Irving, TX",
                LocalDate.of(2026, 8, 14), "https://careers.mckesson.com/en/job/irving/lead/733/99215825472");

        int count = handler.handle(json(eventWithJobs(List.of(job))));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(captor.capture());
        NormalizedJobPosting posting = captor.getValue();
        assertThat(posting.id()).isEqualTo("mckesson:99215825472");
        assertThat(posting.titleRaw()).isEqualTo("Lead Data Architect (Healthcare)");
        assertThat(posting.location()).isEqualTo("Irving, TX");
        assertThat(posting.datePosted()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(posting.url()).isEqualTo(job.url());
        assertThat(posting.firstSeenAt()).isEqualTo(NOW);
    }

    @Test
    void shouldPublishOneNotificationListingEveryScrapedPostingWithOutcomes() throws Exception {
        ScrapedJob newJob = new ScrapedJob("1", "Data Engineer", "Remote", LocalDate.of(2026, 8, 14), "https://x/1");
        ScrapedJob changedJob = new ScrapedJob("2", "Senior Analyst", "Dallas, TX", null, "https://x/2");
        ScrapedJob unchangedJob = new ScrapedJob("3", "Architect", "Irving, TX", LocalDate.of(2026, 8, 1), "https://x/3");

        when(postingRepository.findById("mckesson:2", "mckesson"))
                .thenReturn(Optional.of(stored("2", "sha256:stale", Instant.parse("2026-07-01T00:00:00Z"))));
        // First pass captures the hash computed for job 3, which is then returned as the stored document.
        handler.handle(json(eventWithJobs(List.of(unchangedJob))));
        ArgumentCaptor<NormalizedJobPosting> firstPass = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(firstPass.capture());
        when(postingRepository.findById("mckesson:3", "mckesson")).thenReturn(Optional.of(firstPass.getValue()));

        handler.handle(json(eventWithJobs(List.of(newJob, changedJob, unchangedJob))));

        ArgumentCaptor<DailyNotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(DailyNotificationRequestedEvent.class);
        verify(dailyNotificationPublisher, times(2)).publish(captor.capture());
        DailyNotificationRequestedEvent notification = captor.getValue();

        assertThat(notification.runId()).isEqualTo("run-123");
        assertThat(notification.companyId()).isEqualTo("mckesson");
        assertThat(notification.summaryDate()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(notification.newCount()).isEqualTo(1);
        assertThat(notification.changedCount()).isEqualTo(1);
        assertThat(notification.failures()).containsExactly("detail page 2 timed out");
        assertThat(notification.postings())
                .extracting(NotificationPosting::externalJobId, NotificationPosting::title,
                        NotificationPosting::url, NotificationPosting::outcome)
                .containsExactly(
                        tuple("1", "Data Engineer", "https://x/1", PersistenceOutcome.NEW),
                        tuple("2", "Senior Analyst", "https://x/2", PersistenceOutcome.UPDATED),
                        tuple("3", "Architect", "https://x/3", PersistenceOutcome.UNCHANGED));
    }

    @Test
    void shouldPreserveFirstSeenAtOfExistingPosting() throws Exception {
        Instant firstSeen = Instant.parse("2026-07-01T00:00:00Z");
        when(postingRepository.findById("mckesson:1", "mckesson"))
                .thenReturn(Optional.of(stored("1", "sha256:stale", firstSeen)));

        handler.handle(json(eventWithJobs(List.of(new ScrapedJob("1", "Data Engineer", "Remote", null, null)))));

        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().firstSeenAt()).isEqualTo(firstSeen);
        assertThat(captor.getValue().lastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void shouldDeduplicateJobsByExternalId() throws Exception {
        ScrapedJob job = new ScrapedJob("1", "Data Engineer", "Remote", null, null);

        int count = handler.handle(json(eventWithJobs(List.of(job, job))));

        assertThat(count).isEqualTo(1);
        verify(publisher, times(1)).publish(any(NormalizedJobPosting.class));
    }

    @Test
    void shouldPublishEmptyNotificationWhenNoJobsWereScraped() throws Exception {
        int count = handler.handle(json(eventWithJobs(List.of())));

        assertThat(count).isZero();
        verify(publisher, never()).publish(any());
        ArgumentCaptor<DailyNotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(DailyNotificationRequestedEvent.class);
        verify(dailyNotificationPublisher).publish(captor.capture());
        assertThat(captor.getValue().postings()).isEmpty();
        assertThat(captor.getValue().failures()).containsExactly("detail page 2 timed out");
    }

    @Test
    void shouldFallBackToBlobPathsForLegacyMessagesWithoutJobs() throws Exception {
        // Wire shape of messages produced before the "jobs" field existed.
        String legacyJson = """
                {"runId":"run-123","companyId":"mckesson","startedAt":"2026-08-16T11:00:00Z",
                 "completedAt":"2026-08-16T11:05:00Z","searchPagesFetched":1,"detailPagesFetched":2,
                 "blobPaths":["raw/mckesson/2026/08/16/run-123/job-99215825472.html",
                              "raw/mckesson/2026/08/16/run-123/job-99215825473.html",
                              "raw/mckesson/2026/08/16/run-123/search-page-1.json"],
                 "errors":[]}
                """;

        int count = handler.handle(legacyJson);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(NormalizedJobPosting::id)
                .containsExactly("mckesson:99215825472", "mckesson:99215825473");
        assertThat(captor.getAllValues().get(0).jobLevel()).isEqualTo("Mid-Level");
    }

    @Test
    void shouldExtractExternalJobIdFromWindowsAbsolutePathEvenWhenDirectoryNameContainsJobPrefix() throws Exception {
        // Regression test: a project directory named "job-scraper-function" must not be mistaken
        // for the job-{id}.html file-name prefix.
        String windowsPath = "C:\\Code\\job-scraper\\job-scraper-function\\target\\azure-functions\\"
                + "job-scraper-function-20260815154131779\\.\\output\\raw\\mckesson\\2026\\09\\08\\"
                + "a26c90f0-9b52-41da-8599-dbc1cd9a4d89\\job-100367770672.html";

        handler.handle(json(new ScrapeCompletedEvent(
                "run-123", "mckesson", NOW, NOW, 1, 1, List.of(windowsPath), List.of())));

        ArgumentCaptor<NormalizedJobPosting> captor = ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().externalJobId()).isEqualTo("100367770672");
    }

    @Test
    void shouldNotPublishNotificationWhenPostingPublishFails() throws Exception {
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(any(NormalizedJobPosting.class));
        String json = json(eventWithJobs(List.of(new ScrapedJob("1", "Data Engineer", "Remote", null, null))));

        assertThatThrownBy(() -> handler.handle(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka unavailable");

        verify(dailyNotificationPublisher, never()).publish(any());
    }

    @Test
    void shouldPropagateNotificationPublishFailureSoOffsetIsNotCommitted() throws Exception {
        doThrow(new IllegalStateException("notifications.daily unavailable"))
                .when(dailyNotificationPublisher).publish(any());
        String json = json(eventWithJobs(List.of()));

        assertThatThrownBy(() -> handler.handle(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notifications.daily unavailable");
    }

    private ScrapeCompletedEvent eventWithJobs(List<ScrapedJob> jobs) {
        return new ScrapeCompletedEvent(
                "run-123", "mckesson", NOW, NOW, 1, 0,
                List.of("raw/mckesson/2026/08/16/run-123/search-page-1.json"),
                List.of("detail page 2 timed out"),
                jobs);
    }

    private static NormalizedJobPosting stored(String externalJobId, String contentHash, Instant firstSeen) {
        return new NormalizedJobPosting(
                "mckesson:" + externalJobId, "mckesson", externalJobId, "Old title", "Old title", "Mid-Level",
                "Somewhere", LocalDate.of(2026, 7, 1), firstSeen, firstSeen, true, contentHash, 1);
    }

    private String json(ScrapeCompletedEvent event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }
}

