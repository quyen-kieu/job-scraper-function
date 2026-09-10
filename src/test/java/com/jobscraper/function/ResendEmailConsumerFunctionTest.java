package com.jobscraper.function;

import com.jobscraper.application.notification.DailyNotificationHandler;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendEmailConsumerFunctionTest {

    @Mock
    private DailyNotificationHandler notificationHandler;

    @Test
    void runHandlesDailyNotificationMessageWithoutThrowing() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        when(notificationHandler.handle(any())).thenReturn(new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                2,
                10,
                3,
                1,
                List.of()
        ));

        ResendEmailConsumerFunction function = new ResendEmailConsumerFunction(notificationHandler);

        assertThatCode(() -> function.run("{}", context)).doesNotThrowAnyException();
    }

    @Test
    void runPropagatesHandlerException() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getGlobal());
        doThrow(new IllegalStateException("boom")).when(notificationHandler).handle(any());

        ResendEmailConsumerFunction function = new ResendEmailConsumerFunction(notificationHandler);

        assertThatThrownBy(() -> function.run("{}", context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}

