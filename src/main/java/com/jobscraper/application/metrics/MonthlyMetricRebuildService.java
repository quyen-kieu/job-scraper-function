package com.jobscraper.application.metrics;

import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.metrics.MonthlyMetric;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.MonthlyMetricRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds one month of metrics from persisted observations.
 *
 * <p>This is an internal service operation for rule changes and backfills. It is intentionally
 * not exposed as a Function trigger in Stage 5.</p>
 */
@Service
public class MonthlyMetricRebuildService {

    private final JobObservationRepository observationRepository;
    private final MonthlyMetricRepository metricRepository;

    public MonthlyMetricRebuildService(
            JobObservationRepository observationRepository,
            MonthlyMetricRepository metricRepository) {
        this.observationRepository = observationRepository;
        this.metricRepository = metricRepository;
    }

    public List<MonthlyMetric> rebuildMonth(String companyId, String month) {
        List<JobObservation> observations = observationRepository.findByCompanyAndMonth(companyId, month);

        Map<String, Set<String>> groupedKeys = new HashMap<>();
        Map<String, String> groupedTitle = new HashMap<>();
        Map<String, String> groupedLevel = new HashMap<>();

        for (JobObservation observation : observations) {
            String bucketKey = observation.titleNormalized() + "|" + observation.jobLevel();
            groupedKeys.computeIfAbsent(bucketKey, ignored -> new HashSet<>())
                    .add(MonthlyMetric.observationKey(observation.externalJobId(), observation.scrapeDate()));
            groupedTitle.putIfAbsent(bucketKey, observation.titleNormalized());
            groupedLevel.putIfAbsent(bucketKey, observation.jobLevel());
        }

        List<MonthlyMetric> rebuilt = new ArrayList<>();
        Instant calculatedAt = Instant.now();

        for (Map.Entry<String, Set<String>> entry : groupedKeys.entrySet()) {
            String bucketKey = entry.getKey();
            String normalizedTitle = groupedTitle.get(bucketKey);
            String jobLevel = groupedLevel.get(bucketKey);

            MonthlyMetric metric = new MonthlyMetric(
                    MonthlyMetric.buildId(companyId, month, normalizedTitle, jobLevel),
                    companyId,
                    month,
                    normalizedTitle,
                    jobLevel,
                    entry.getValue(),
                    calculatedAt
            );

            metricRepository.upsert(metric);
            rebuilt.add(metric);
        }

        return rebuilt;
    }
}

