package com.jobscraper.application.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.domain.metrics.MonthlyMetric;
import com.jobscraper.infrastructure.cosmos.MonthlyMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyMetricAggregationHandlerTest {

    @Mock
    private MonthlyMetricRepository repository;

    @Mock
    private MonthlyMetricPublisher publisher;

    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private MonthlyMetricAggregationHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        fixedClock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);
        handler = new MonthlyMetricAggregationHandler(objectMapper, repository, publisher, fixedClock);
    }

    @Test
    void shouldCreateNewMetricWithCountOneOnFirstObservation() throws Exception {
        NormalizedJobPosting posting = posting("9921", Instant.parse("2026-08-16T10:00:00Z"));
        when(repository.findById(any(), any())).thenReturn(Optional.empty());

        MonthlyMetric metric = handler.handle(objectMapper.writeValueAsString(posting));

        assertThat(metric.postingCount()).isEqualTo(1);
        assertThat(metric.uniquePostingCount()).isEqualTo(1);
        verify(repository).upsert(metric);
    }

    @Test
    void shouldNotInflateCountsOnExactRedeliveryOfSameEvent() throws Exception {
        NormalizedJobPosting posting = posting("9921", Instant.parse("2026-08-16T10:00:00Z"));
        MonthlyMetric existing = new MonthlyMetric(
                MonthlyMetric.buildId("mckesson", "2026-08", posting.titleNormalized(), posting.jobLevel()),
                "mckesson",
                "2026-08",
                posting.titleNormalized(),
                posting.jobLevel(),
                Set.of(MonthlyMetric.observationKey("9921", LocalDate.of(2026, 8, 16))),
                Instant.parse("2026-08-16T10:01:00Z")
        );

        when(repository.findById(existing.id(), "2026-08")).thenReturn(Optional.of(existing));

        MonthlyMetric updated = handler.handle(objectMapper.writeValueAsString(posting));

        assertThat(updated.postingCount()).isEqualTo(1);
        assertThat(updated.uniquePostingCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementPostingCountButNotUniqueCountForSameJobOnDifferentDay() throws Exception {
        NormalizedJobPosting posting = posting("9921", Instant.parse("2026-08-17T10:00:00Z"));
        MonthlyMetric existing = new MonthlyMetric(
                MonthlyMetric.buildId("mckesson", "2026-08", posting.titleNormalized(), posting.jobLevel()),
                "mckesson",
                "2026-08",
                posting.titleNormalized(),
                posting.jobLevel(),
                Set.of(MonthlyMetric.observationKey("9921", LocalDate.of(2026, 8, 16))),
                Instant.parse("2026-08-16T10:01:00Z")
        );

        when(repository.findById(existing.id(), "2026-08")).thenReturn(Optional.of(existing));

        MonthlyMetric updated = handler.handle(objectMapper.writeValueAsString(posting));

        assertThat(updated.postingCount()).isEqualTo(2);
        assertThat(updated.uniquePostingCount()).isEqualTo(1);
    }

    @Test
    void shouldIncrementBothCountsForDifferentExternalJobId() throws Exception {
        NormalizedJobPosting posting = posting("9922", Instant.parse("2026-08-17T10:00:00Z"));
        MonthlyMetric existing = new MonthlyMetric(
                MonthlyMetric.buildId("mckesson", "2026-08", posting.titleNormalized(), posting.jobLevel()),
                "mckesson",
                "2026-08",
                posting.titleNormalized(),
                posting.jobLevel(),
                Set.of(MonthlyMetric.observationKey("9921", LocalDate.of(2026, 8, 16))),
                Instant.parse("2026-08-16T10:01:00Z")
        );

        when(repository.findById(existing.id(), "2026-08")).thenReturn(Optional.of(existing));

        MonthlyMetric updated = handler.handle(objectMapper.writeValueAsString(posting));

        assertThat(updated.postingCount()).isEqualTo(2);
        assertThat(updated.uniquePostingCount()).isEqualTo(2);
    }

    @Test
    void shouldPropagateRepositoryFailure() throws Exception {
        NormalizedJobPosting posting = posting("9921", Instant.parse("2026-08-16T10:00:00Z"));
        when(repository.findById(any(), any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Cosmos unavailable")).when(repository).upsert(any());

        assertThatThrownBy(() -> handler.handle(objectMapper.writeValueAsString(posting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cosmos unavailable");
    }

    @Test
    void shouldPropagatePublisherFailure() throws Exception {
        NormalizedJobPosting posting = posting("9921", Instant.parse("2026-08-16T10:00:00Z"));
        when(repository.findById(any(), any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(any());

        assertThatThrownBy(() -> handler.handle(objectMapper.writeValueAsString(posting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka unavailable");
    }

    private NormalizedJobPosting posting(String externalJobId, Instant firstSeenAt) {
        return new NormalizedJobPosting(
                "mckesson:" + externalJobId,
                "mckesson",
                externalJobId,
                "Lead Data Architect (Healthcare)",
                "Data Architect",
                "Lead",
                "Irving, TX",
                LocalDate.of(2026, 8, 14),
                firstSeenAt,
                firstSeenAt,
                true,
                "sha256:abc123",
                1
        );
    }
}
