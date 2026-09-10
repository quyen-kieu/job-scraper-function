package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RunCompletionAggregator}.
 *
 * <p><b>Known limitation:</b> state lives only in this process's heap (a {@link ConcurrentHashMap}),
 * so it does not survive a host restart and does not span multiple Function App instances. This is
 * sufficient for the current single-instance local/dev deployment model documented in the README's
 * "Single Function App deployment" section. A durable (Cosmos-backed) run registry is a natural
 * Stage 7/8 hardening item if the app is ever scaled to multiple concurrently-running instances.</p>
 */
@Component
public class InMemoryRunCompletionAggregator implements RunCompletionAggregator {

    private final ConcurrentHashMap<String, CompanyRunState> statesByCompanyId = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRunCompletionAggregator() {
        this(Clock.systemUTC());
    }

    InMemoryRunCompletionAggregator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<DailyNotificationRequestedEvent> registerExpectedCompany(
            String runId, String companyId, int expectedPostings, List<String> scrapeErrors) {
        if (expectedPostings <= 0) {
            // Nothing to wait for: the scrape produced no postings to persist, so the summary
            // is already complete.
            return Optional.of(buildEvent(runId, 0, 0, scrapeErrors));
        }

        statesByCompanyId.put(companyId, new CompanyRunState(runId, expectedPostings, scrapeErrors));
        return Optional.empty();
    }

    @Override
    public Optional<DailyNotificationRequestedEvent> recordPersistedPosting(
            String companyId, PersistenceOutcome outcome) {
        CompanyRunState state = statesByCompanyId.get(companyId);
        if (state == null) {
            // Defensive: no matching registration (e.g. redelivery after a prior completion
            // already removed the entry, or registration/persistence ordering race). Nothing to
            // aggregate into; the posting was still persisted successfully by the caller.
            return Optional.empty();
        }

        synchronized (state) {
            state.received++;
            if (outcome == PersistenceOutcome.NEW) {
                state.newCount++;
            } else if (outcome == PersistenceOutcome.UPDATED) {
                state.changedCount++;
            }

            if (state.received < state.expectedPostings) {
                return Optional.empty();
            }

            statesByCompanyId.remove(companyId, state);
            return Optional.of(buildEvent(state.runId, state.newCount, state.changedCount, state.errors));
        }
    }

    private DailyNotificationRequestedEvent buildEvent(
            String runId, int newCount, int changedCount, List<String> errors) {
        return new DailyNotificationRequestedEvent(
                runId,
                LocalDate.now(clock),
                1,
                newCount,
                changedCount,
                // Detecting postings that disappeared from a scrape (went inactive) requires a
                // reconciliation step against previously-active postings that is not yet
                // implemented; always 0 for now (documented Stage 6 scope decision).
                0,
                List.copyOf(errors)
        );
    }

    private static final class CompanyRunState {
        private final String runId;
        private final int expectedPostings;
        private final List<String> errors;
        private int received;
        private int newCount;
        private int changedCount;

        private CompanyRunState(String runId, int expectedPostings, List<String> errors) {
            this.runId = runId;
            this.expectedPostings = expectedPostings;
            this.errors = List.copyOf(errors);
        }
    }
}




