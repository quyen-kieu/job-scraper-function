package com.jobscraper.domain.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Precomputed monthly aggregation document.
 *
 * <p>Persisted in the Cosmos DB {@code monthly-metrics} container with partition key
 * {@code /month}. The {@code observedJobDates} set stores deterministic composite keys in
 * the form {@code externalJobId:scrapeDate}. Set semantics make aggregation idempotent
 * under Kafka redelivery.</p>
 */
public record MonthlyMetric(
        String id,
        String companyId,
        String month,
        String normalizedTitle,
        String jobLevel,
        Set<String> observedJobDates,
        Instant lastCalculatedAt) {

    public MonthlyMetric {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(normalizedTitle, "normalizedTitle must not be null");
        Objects.requireNonNull(jobLevel, "jobLevel must not be null");
        Objects.requireNonNull(lastCalculatedAt, "lastCalculatedAt must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }

        if (month.isBlank()) {
            throw new IllegalArgumentException("month must not be blank");
        }

        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("normalizedTitle must not be blank");
        }

        if (jobLevel.isBlank()) {
            throw new IllegalArgumentException("jobLevel must not be blank");
        }

        observedJobDates = Set.copyOf(Objects.requireNonNullElse(observedJobDates, Set.of()));
    }

    public int postingCount() {
        return observedJobDates.size();
    }

    public int uniquePostingCount() {
        return observedJobDates.stream()
                .map(value -> {
                    int separator = value.indexOf(':');
                    return separator > 0 ? value.substring(0, separator) : value;
                })
                .collect(java.util.stream.Collectors.toSet())
                .size();
    }

    public static String buildId(String companyId, String month, String normalizedTitle, String jobLevel) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(normalizedTitle, "normalizedTitle must not be null");
        Objects.requireNonNull(jobLevel, "jobLevel must not be null");

        return companyId
                + "-" + month
                + "-" + slugify(normalizedTitle)
                + "-" + slugify(jobLevel);
    }

    public static String observationKey(String externalJobId, LocalDate scrapeDate) {
        Objects.requireNonNull(externalJobId, "externalJobId must not be null");
        Objects.requireNonNull(scrapeDate, "scrapeDate must not be null");
        return externalJobId + ":" + scrapeDate;
    }

    public MonthlyMetric withObservation(String externalJobId, LocalDate scrapeDate, Instant calculatedAt) {
        Set<String> merged = new HashSet<>(observedJobDates);
        merged.add(observationKey(externalJobId, scrapeDate));
        return new MonthlyMetric(id, companyId, month, normalizedTitle, jobLevel, merged, calculatedAt);
    }

    private static String slugify(String value) {
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "")
                .replaceAll("-{2,}", "-");
        return slug.isBlank() ? "unknown" : slug;
    }
}

