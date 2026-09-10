package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRunCompletionAggregatorTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void registerExpectedCompanyReturnsImmediateEventWhenExpectedPostingsIsZero() {
        InMemoryRunCompletionAggregator aggregator = new InMemoryRunCompletionAggregator(fixedClock);

        Optional<DailyNotificationRequestedEvent> result =
                aggregator.registerExpectedCompany("run-123", "mckesson", 0, List.of("timeout"));

        assertThat(result).isPresent();
        DailyNotificationRequestedEvent event = result.get();
        assertThat(event.runId()).isEqualTo("run-123");
        assertThat(event.summaryDate()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(event.companiesProcessed()).isEqualTo(1);
        assertThat(event.newCount()).isZero();
        assertThat(event.changedCount()).isZero();
        assertThat(event.inactiveCount()).isZero();
        assertThat(event.failures()).containsExactly("timeout");
    }

    @Test
    void registerExpectedCompanyReturnsEmptyWhenExpectedPostingsIsPositive() {
        InMemoryRunCompletionAggregator aggregator = new InMemoryRunCompletionAggregator(fixedClock);

        Optional<DailyNotificationRequestedEvent> result =
                aggregator.registerExpectedCompany("run-123", "mckesson", 3, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void recordPersistedPostingReturnsEmptyUntilAllExpectedPostingsRecorded() {
        InMemoryRunCompletionAggregator aggregator = new InMemoryRunCompletionAggregator(fixedClock);
        aggregator.registerExpectedCompany("run-123", "mckesson", 3, List.of());

        assertThat(aggregator.recordPersistedPosting("mckesson", PersistenceOutcome.NEW)).isEmpty();
        assertThat(aggregator.recordPersistedPosting("mckesson", PersistenceOutcome.UPDATED)).isEmpty();

        Optional<DailyNotificationRequestedEvent> result =
                aggregator.recordPersistedPosting("mckesson", PersistenceOutcome.UNCHANGED);

        assertThat(result).isPresent();
        DailyNotificationRequestedEvent event = result.get();
        assertThat(event.runId()).isEqualTo("run-123");
        assertThat(event.newCount()).isEqualTo(1);
        assertThat(event.changedCount()).isEqualTo(1);
        assertThat(event.companiesProcessed()).isEqualTo(1);
    }

    @Test
    void recordPersistedPostingIgnoresUnregisteredCompany() {
        InMemoryRunCompletionAggregator aggregator = new InMemoryRunCompletionAggregator(fixedClock);

        Optional<DailyNotificationRequestedEvent> result =
                aggregator.recordPersistedPosting("unregistered-company", PersistenceOutcome.NEW);

        assertThat(result).isEmpty();
    }

    @Test
    void completingACompanyRunRemovesItsStateSoAFollowUpRunStartsFresh() {
        InMemoryRunCompletionAggregator aggregator = new InMemoryRunCompletionAggregator(fixedClock);
        aggregator.registerExpectedCompany("run-123", "mckesson", 1, List.of());
        assertThat(aggregator.recordPersistedPosting("mckesson", PersistenceOutcome.NEW)).isPresent();

        // A second scrape run for the same company should not be affected by the first run's
        // already-completed (and removed) state.
        aggregator.registerExpectedCompany("run-456", "mckesson", 1, List.of());
        Optional<DailyNotificationRequestedEvent> secondRun =
                aggregator.recordPersistedPosting("mckesson", PersistenceOutcome.NEW);

        assertThat(secondRun).isPresent();
        assertThat(secondRun.get().runId()).isEqualTo("run-456");
    }
}

