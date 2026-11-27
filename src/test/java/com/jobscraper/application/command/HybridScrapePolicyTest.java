package com.jobscraper.application.command;

import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridScrapePolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private final JobPostingRepository repository = mock(JobPostingRepository.class);
    private final HybridScrapePolicy policy = new HybridScrapePolicy(repository);
    private final ScrapeCompanyCommand command = new ScrapeCompanyCommand("run", "company", NOW);
    private final ScrapedJob job = new ScrapedJob("1", "Engineer", "Texas",
            LocalDate.of(2026, 11, 1), "https://example.test/1", null);

    @Test
    void firstRunAndWeeklyRunsFetchEveryDetail() {
        when(repository.lastSuccessfulReconciliationAt("company")).thenReturn(Optional.empty(),
                Optional.of(NOW.minusSeconds(7 * 86400).plusSeconds(120)));
        assertThat(policy.createContext(command, NOW).fullDetailRefresh()).isTrue();
        assertThat(policy.createContext(command, NOW).shouldFetchDetail(job, false)).isTrue();
    }

    @Test
    void incrementalRunFetchesNewChangedInactiveAndMissingDescription() {
        when(repository.lastSuccessfulReconciliationAt("company")).thenReturn(Optional.of(NOW.minusSeconds(86400)));
        var context = policy.createContext(command, NOW);
        assertThat(context.fullDetailRefresh()).isFalse();
        when(repository.findById("company:1", "company")).thenReturn(Optional.empty());
        assertThat(context.shouldFetchDetail(job, false)).isTrue();
        NormalizedJobPosting existing = posting().withDescription("Full description");
        when(repository.findById("company:1", "company")).thenReturn(Optional.of(existing));
        assertThat(context.shouldFetchDetail(job, false)).isFalse();
        assertThat(context.shouldFetchDetail(new ScrapedJob("1", "Senior Engineer", "Texas",
                job.datePosted(), job.url(), null), false)).isTrue();
        assertThat(context.shouldFetchDetail(new ScrapedJob("1", "Engineer", "Texas",
                job.datePosted().plusDays(1), job.url(), null), true)).isTrue();
        assertThat(context.shouldFetchDetail(new ScrapedJob("1", "Engineer", "Texas",
                job.datePosted().plusDays(1), job.url(), null), false)).isFalse();
        when(repository.findById("company:1", "company")).thenReturn(Optional.of(existing.withActive(false)));
        assertThat(context.shouldFetchDetail(job, false)).isTrue();
        when(repository.findById("company:1", "company")).thenReturn(Optional.of(posting()));
        assertThat(context.shouldFetchDetail(job, false)).isTrue();
    }

    private NormalizedJobPosting posting() {
        return new NormalizedJobPosting("company:1", "company", "1", job.title(), "Engineer",
                "Mid-Level", job.location(), job.datePosted(), NOW, NOW, true, "hash", 1, job.url());
    }
}
