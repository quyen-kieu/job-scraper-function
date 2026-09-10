package com.jobscraper.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyNotificationHandlerTest {

    @Mock
    private DailyNotificationSender sender;

    @Mock
    private SentNotificationGuard sentNotificationGuard;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        lenient().when(sentNotificationGuard.alreadySent(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    }

    @Test
    void handleParsesEventAndDelegatesToSender() throws Exception {
        DailyNotificationHandler handler = new DailyNotificationHandler(objectMapper, sender, sentNotificationGuard);

        DailyNotificationRequestedEvent request = new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                5,
                14,
                6,
                2,
                List.of("oracle timeout")
        );

        DailyNotificationRequestedEvent result = handler.handle(objectMapper.writeValueAsString(request));

        assertThat(result).isEqualTo(request);
        verify(sender).send(request);
        verify(sentNotificationGuard).markSent("run-2026-08-23");
    }

    @Test
    void handleSkipsSendingWhenNotificationForRunIdAlreadySent() throws Exception {
        DailyNotificationHandler handler = new DailyNotificationHandler(objectMapper, sender, sentNotificationGuard);

        DailyNotificationRequestedEvent request = new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                5,
                14,
                6,
                2,
                List.of("oracle timeout")
        );

        when(sentNotificationGuard.alreadySent("run-2026-08-23")).thenReturn(true);

        DailyNotificationRequestedEvent result = handler.handle(objectMapper.writeValueAsString(request));

        assertThat(result).isEqualTo(request);
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
        verify(sentNotificationGuard, never()).markSent(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePropagatesSenderFailure() throws Exception {
        DailyNotificationHandler handler = new DailyNotificationHandler(objectMapper, sender, sentNotificationGuard);

        DailyNotificationRequestedEvent request = new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                5,
                14,
                6,
                2,
                List.of()
        );

        org.mockito.Mockito.doThrow(new IllegalStateException("send failed"))
                .when(sender).send(request);

        assertThatThrownBy(() -> handler.handle(objectMapper.writeValueAsString(request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("send failed");

        verify(sentNotificationGuard, never()).markSent(org.mockito.ArgumentMatchers.anyString());
    }
}

