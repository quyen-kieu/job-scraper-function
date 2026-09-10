package com.jobscraper.application.metrics;

import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.metrics.MonthlyMetric;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.MonthlyMetricRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyMetricRebuildServiceTest {

    @Mock
    private JobObservationRepository observationRepository;

    @Mock
    private MonthlyMetricRepository metricRepository;

    @Test
    void shouldRebuildGroupedMetricsFromObservations() {
        MonthlyMetricRebuildService service = new MonthlyMetricRebuildService(observationRepository, metricRepository);

        when(observationRepository.findByCompanyAndMonth("mckesson", "2026-08")).thenReturn(List.of(
                observation("mckesson", "9921", "Data Architect", "Lead", LocalDate.of(2026, 8, 16)),
                observation("mckesson", "9921", "Data Architect", "Lead", LocalDate.of(2026, 8, 17)),
                observation("mckesson", "9922", "Data Architect", "Lead", LocalDate.of(2026, 8, 17))
        ));

        List<MonthlyMetric> rebuilt = service.rebuildMonth("mckesson", "2026-08");

        assertThat(rebuilt).hasSize(1);
        assertThat(rebuilt.get(0).postingCount()).isEqualTo(3);
        assertThat(rebuilt.get(0).uniquePostingCount()).isEqualTo(2);
        verify(metricRepository, times(1)).upsert(any(MonthlyMetric.class));
    }

    @Test
    void shouldProduceDistinctBucketsPerTitleAndLevel() {
        MonthlyMetricRebuildService service = new MonthlyMetricRebuildService(observationRepository, metricRepository);

        when(observationRepository.findByCompanyAndMonth("mckesson", "2026-08")).thenReturn(List.of(
                observation("mckesson", "9921", "Data Architect", "Lead", LocalDate.of(2026, 8, 16)),
                observation("mckesson", "9922", "Software Engineer", "Senior", LocalDate.of(2026, 8, 16))
        ));

        List<MonthlyMetric> rebuilt = service.rebuildMonth("mckesson", "2026-08");

        assertThat(rebuilt).hasSize(2);
        verify(metricRepository, times(2)).upsert(any(MonthlyMetric.class));
    }

    private JobObservation observation(
            String companyId,
            String externalJobId,
            String normalizedTitle,
            String jobLevel,
            LocalDate scrapeDate) {
        return new JobObservation(
                JobObservation.buildId(companyId, scrapeDate, externalJobId),
                companyId,
                externalJobId,
                "run-123",
                Instant.parse("2026-08-16T10:00:00Z"),
                scrapeDate,
                LocalDate.of(2026, 8, 14),
                normalizedTitle,
                jobLevel,
                "sha256:abc123",
                1
        );
    }
}

