package com.jobscraper.application.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.domain.metrics.MonthlyMetric;
import com.jobscraper.infrastructure.cosmos.MonthlyMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Aggregates monthly metrics from normalized posting events.
 */
@Service
public class MonthlyMetricAggregationHandler {

    private final ObjectMapper objectMapper;
    private final MonthlyMetricRepository repository;
    private final MonthlyMetricPublisher publisher;
    private final Clock clock;

    @Autowired
    public MonthlyMetricAggregationHandler(
            ObjectMapper objectMapper,
            MonthlyMetricRepository repository,
            MonthlyMetricPublisher publisher) {
        this(objectMapper, repository, publisher, Clock.systemUTC());
    }

    MonthlyMetricAggregationHandler(
            ObjectMapper objectMapper,
            MonthlyMetricRepository repository,
            MonthlyMetricPublisher publisher,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Handles one normalized posting event and updates/publishes monthly metrics.
     *
     * <p>Month bucketing is based on {@code firstSeenAt}, not {@code datePosted}, so aggregation
     * remains stable even when upstream date-posted parsing is missing or inconsistent.</p>
     *
     * @param json raw JSON event
     * @return updated monthly metric document
     * @throws Exception any deserialization, persistence, or publish error
     */
    public MonthlyMetric handle(String json) throws Exception {
        NormalizedJobPosting posting = objectMapper.readValue(json, NormalizedJobPosting.class);

        LocalDate scrapeDate = posting.firstSeenAt().atZone(ZoneOffset.UTC).toLocalDate();
        String month = YearMonth.from(scrapeDate).toString();

        String id = MonthlyMetric.buildId(
                posting.companyId(),
                month,
                posting.titleNormalized(),
                posting.jobLevel()
        );

        Instant calculatedAt = Instant.now(clock);

        MonthlyMetric metric = repository.findById(id, month)
                .map(existing -> existing.withObservation(posting.externalJobId(), scrapeDate, calculatedAt))
                .orElseGet(() -> new MonthlyMetric(
                        id,
                        posting.companyId(),
                        month,
                        posting.titleNormalized(),
                        posting.jobLevel(),
                        java.util.Set.of(MonthlyMetric.observationKey(posting.externalJobId(), scrapeDate)),
                        calculatedAt
                ));

        repository.upsert(metric);

        MonthlyMetricPublishedEvent event = new MonthlyMetricPublishedEvent(
                metric.companyId(),
                metric.month(),
                metric.normalizedTitle(),
                metric.jobLevel(),
                metric.postingCount(),
                metric.uniquePostingCount(),
                metric.lastCalculatedAt()
        );
        publisher.publish(event);

        return metric;
    }
}
