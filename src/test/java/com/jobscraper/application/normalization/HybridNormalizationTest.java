package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.infrastructure.cosmos.ReconciliationResult;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridNormalizationTest {
    private static final Instant START = Instant.parse("2026-11-27T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final NormalizedPostingPublisher publisher = mock(NormalizedPostingPublisher.class);
    private final JobPostingRepository repository = mock(JobPostingRepository.class);
    private final DailyNotificationPublisher notifications = mock(DailyNotificationPublisher.class);
    private final NormalizedPostingPersistenceHandler persistence = mock(NormalizedPostingPersistenceHandler.class);
    private ScrapeCompletedNormalizationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScrapeCompletedNormalizationHandler(mapper, publisher, repository, notifications,
                persistence, Clock.fixed(START.plusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    void persistsEveryPostingBeforeReconcilingAndPublishesMarkedEvents() throws Exception {
        when(repository.findById(anyString(), anyString())).thenReturn(Optional.empty());
        when(persistence.persist(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        when(repository.reconcile("company", "run", START, Set.of("1", "2")))
                .thenReturn(new ReconciliationResult(true, 3));

        assertThat(handler.handle(json(event(true, true, List.of(), List.of(job("1"), job("2")))))).isEqualTo(2);

        var order = inOrder(persistence, repository, notifications);
        order.verify(persistence).persist(any());
        order.verify(persistence).persist(any());
        order.verify(repository).reconcile("company", "run", START, Set.of("1", "2"));
        order.verify(notifications).publish(any());
        var postings = org.mockito.ArgumentCaptor.forClass(NormalizedJobPosting.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(postings.capture());
        assertThat(postings.getAllValues()).allSatisfy(posting -> {
            assertThat(posting.persistedSynchronously()).isTrue();
            assertThat(posting.lastSeenAt()).isEqualTo(START);
            assertThat(posting.scrapeRunId()).isEqualTo("run");
        });
        var notification = org.mockito.ArgumentCaptor.forClass(DailyNotificationRequestedEvent.class);
        verify(notifications).publish(notification.capture());
        assertThat(notification.getValue().inactiveCount()).isEqualTo(3);
    }

    @Test
    void doesNotReconcilePartialFailedOrDailyRuns() throws Exception {
        when(repository.findById(anyString(), anyString())).thenReturn(Optional.empty());
        when(persistence.persist(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        handler.handle(json(event(false, true, List.of(), List.of(job("1")))));
        handler.handle(json(event(true, true, List.of("detail failed"), List.of(job("1")))));
        handler.handle(json(event(true, false, List.of(), List.of(job("1")))));
        verify(repository, never()).reconcile(anyString(), anyString(), any(), any());
    }

    @Test
    void persistenceFailurePreventsReconciliationAndNotification() throws Exception {
        when(repository.findById(anyString(), anyString())).thenReturn(Optional.empty());
        when(persistence.persist(any())).thenThrow(new IllegalStateException("Cosmos unavailable"));
        assertThatThrownBy(() -> handler.handle(json(event(true, true, List.of(), List.of(job("1"))))))
                .hasMessage("Cosmos unavailable");
        verify(repository, never()).reconcile(anyString(), anyString(), any(), any());
        verify(notifications, never()).publish(any());
    }

    @Test
    void suspiciousEmptyScanReportsSkippedReconciliation() throws Exception {
        when(repository.reconcile(eq("company"), eq("run"), eq(START), eq(Set.of())))
                .thenReturn(ReconciliationResult.skipped());
        handler.handle(json(event(true, true, List.of(), List.of())));
        var notification = org.mockito.ArgumentCaptor.forClass(DailyNotificationRequestedEvent.class);
        verify(notifications).publish(notification.capture());
        assertThat(notification.getValue().failures()).anyMatch(error -> error.contains("Reconciliation skipped"));
    }

    @Test
    void incrementalNotificationRetainsStoredSalaryAndPostingDate() throws Exception {
        LocalDate posted = LocalDate.of(2026, 11, 1);
        NormalizedJobPosting existing = new NormalizedJobPosting("company:1", "company", "1",
                "Engineer", "Engineer", "Mid-Level", "Texas", posted, START.minusSeconds(86400),
                START.minusSeconds(86400), true, "hash", 1, "https://example.test/1")
                .withDescription("Description").withSalaryRange("$100,000 - $120,000");
        when(repository.findById("company:1", "company")).thenReturn(Optional.of(existing));
        when(persistence.persist(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        handler.handle(json(event(true, false, List.of(), List.of(job("1")))));

        var notification = org.mockito.ArgumentCaptor.forClass(DailyNotificationRequestedEvent.class);
        verify(notifications).publish(notification.capture());
        assertThat(notification.getValue().postings()).singleElement().satisfies(posting -> {
            assertThat(posting.datePosted()).isEqualTo(posted);
            assertThat(posting.salaryRange()).isEqualTo("$100,000 - $120,000");
        });
    }

    private ScrapeCompletedEvent event(boolean complete, boolean full, List<String> errors, List<ScrapedJob> jobs) {
        return new ScrapeCompletedEvent("run", "company", START, START.plusSeconds(30),
                1, jobs.size(), List.of(), errors, jobs, complete, full);
    }

    private static ScrapedJob job(String id) {
        return new ScrapedJob(id, "Engineer", "Texas", null, "https://example.test/" + id, null);
    }

    private String json(ScrapeCompletedEvent event) throws Exception {
        return mapper.writeValueAsString(event);
    }
}
