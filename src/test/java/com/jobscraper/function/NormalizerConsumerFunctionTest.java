package com.jobscraper.function;

import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalizerConsumerFunctionTest {

    @Mock
    private ScrapeCompletedNormalizationHandler normalizationHandler;

    @Test
    void shouldCreateNormalizerConsumerFunctionWithDependencies() {
        NormalizerConsumerFunction function = new NormalizerConsumerFunction(normalizationHandler);

        assertThat(function).isNotNull();
    }

    @Test
    void shouldDelegateToHandlerWithoutThrowing() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        when(normalizationHandler.handle(any())).thenReturn(0);

        NormalizerConsumerFunction function = new NormalizerConsumerFunction(normalizationHandler);

        assertThatCode(() -> function.run("{}", context)).doesNotThrowAnyException();
    }

    @Test
    void shouldPropagateHandlerException() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        doThrow(new IllegalStateException("boom")).when(normalizationHandler).handle(any());

        NormalizerConsumerFunction function = new NormalizerConsumerFunction(normalizationHandler);

        assertThatThrownBy(() -> function.run("{}", context))
                .isInstanceOf(IllegalStateException.class);
    }
}

