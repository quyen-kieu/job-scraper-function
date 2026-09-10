package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.metrics.MonthlyMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmosMonthlyMetricRepositoryTest {

    @Mock
    private CosmosContainer container;

    @Mock
    private CosmosItemResponse<MonthlyMetric> itemResponse;

    @Test
    void shouldUpsertUsingMonthPartitionKey() {
        CosmosMonthlyMetricRepository repository = new CosmosMonthlyMetricRepository(container);
        MonthlyMetric metric = buildMetric();

        repository.upsert(metric);

        verify(container).upsertItem(
                eq(metric),
                eq(new PartitionKey("2026-08")),
                any(CosmosItemRequestOptions.class));
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        CosmosMonthlyMetricRepository repository = new CosmosMonthlyMetricRepository(container);

        CosmosException notFound = mock(CosmosException.class);
        when(notFound.getStatusCode()).thenReturn(404);
        when(container.readItem(eq("metric-id"), any(PartitionKey.class), eq(MonthlyMetric.class)))
                .thenThrow(notFound);

        Optional<MonthlyMetric> result = repository.findById("metric-id", "2026-08");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnExistingMetricWhenFound() {
        CosmosMonthlyMetricRepository repository = new CosmosMonthlyMetricRepository(container);
        MonthlyMetric metric = buildMetric();

        when(itemResponse.getItem()).thenReturn(metric);
        when(container.readItem(eq(metric.id()), any(PartitionKey.class), eq(MonthlyMetric.class)))
                .thenReturn(itemResponse);

        Optional<MonthlyMetric> result = repository.findById(metric.id(), metric.month());

        assertThat(result).contains(metric);
    }

    private MonthlyMetric buildMetric() {
        return new MonthlyMetric(
                "metric-id",
                "mckesson",
                "2026-08",
                "Data Architect",
                "Mid-Level",
                Set.of("9921:2026-08-16"),
                Instant.now()
        );
    }
}

