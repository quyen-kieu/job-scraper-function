package com.jobscraper.function;

import com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistenceConsumerFunctionTest {

    @Mock
    private NormalizedPostingPersistenceHandler persistenceHandler;

    @Test
    void shouldCreatePersistenceConsumerFunctionWithDependencies() {
        PersistenceConsumerFunction function = new PersistenceConsumerFunction(persistenceHandler);

        assertThat(function).isNotNull();
    }

    @Test
    void shouldDelegateToHandlerAndLogSuccessfulPersistence() throws Exception {
        var context = mock(com.microsoft.azure.functions.ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());

        NormalizedJobPosting posting = new NormalizedJobPosting(
                "mckesson:99215825472", "mckesson", "99215825472",
                "Lead Data Architect", "Data Architect", "Lead", "Irving, TX",
                LocalDate.of(2026, 8, 14), Instant.now(), Instant.now(), true, "sha256:abc", 1
        );
        when(persistenceHandler.handle(any())).thenReturn(posting);

        PersistenceConsumerFunction function = new PersistenceConsumerFunction(persistenceHandler);

        assertThatCode(() -> function.run("{}", context)).doesNotThrowAnyException();
    }

    @Test
    void shouldPropagateHandlerException() throws Exception {
        var context = mock(com.microsoft.azure.functions.ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());

        doThrow(new IllegalStateException("Cosmos unavailable")).when(persistenceHandler).handle(any());

        PersistenceConsumerFunction function = new PersistenceConsumerFunction(persistenceHandler);

        assertThatThrownBy(() -> function.run("{}", context))
                .isInstanceOf(IllegalStateException.class);
    }
}
