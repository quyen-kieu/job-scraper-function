package com.jobscraper.function;

import com.jobscraper.application.metrics.MonthlyMetricAggregationHandler;
import com.jobscraper.domain.metrics.MonthlyMetric;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsConsumerFunctionTest {

    @Mock
    private MonthlyMetricAggregationHandler aggregationHandler;

    @Test
    void shouldCreateMetricsConsumerFunctionWithDependencies() {
        MetricsConsumerFunction function = new MetricsConsumerFunction(aggregationHandler);

        assertThat(function).isNotNull();
    }

    @Test
    void shouldDelegateToHandlerWithoutThrowing() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());

        MonthlyMetric metric = new MonthlyMetric(
                "mckesson-2026-08-data-architect-mid-level",
                "mckesson",
                "2026-08",
                "Data Architect",
                "Mid-Level",
                Set.of("9921:2026-08-16"),
                Instant.now()
        );

        when(aggregationHandler.handle(any())).thenReturn(metric);

        MetricsConsumerFunction function = new MetricsConsumerFunction(aggregationHandler);

        assertThatCode(() -> function.run("{}", context)).doesNotThrowAnyException();
    }

    @Test
    void shouldPropagateHandlerException() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        doThrow(new IllegalStateException("boom")).when(aggregationHandler).handle(any());

        MetricsConsumerFunction function = new MetricsConsumerFunction(aggregationHandler);

        assertThatThrownBy(() -> function.run("{}", context))
                .isInstanceOf(IllegalStateException.class);
    }
}
