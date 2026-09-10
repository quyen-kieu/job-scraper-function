package com.jobscraper.application.notification;

import com.jobscraper.configuration.ResendProperties;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendDailyNotificationSenderTest {

    @Mock
    private Resend resendClient;

    @Mock
    private Emails emailsService;

    private final ResendProperties properties = buildProperties();

    @Test
    void sendPostsEmailAndSucceedsOnSuccessfulResponse() throws Exception {
        CreateEmailResponse response = new CreateEmailResponse();
        response.setId("re_test_id_123");
        when(resendClient.emails()).thenReturn(emailsService);
        when(emailsService.send(any(CreateEmailOptions.class))).thenReturn(response);

        ResendDailyNotificationSender sender = new ResendDailyNotificationSender(resendClient, properties);
        DailyNotificationRequestedEvent event = buildEvent();

        sender.send(event);

        CreateEmailOptions request = sender.buildRequest(event);
        assertThat(request.getSubject()).contains("2026-08-23");
    }

    @Test
    void sendThrowsWhenResendThrowsResendException() throws Exception {
        when(resendClient.emails()).thenReturn(emailsService);
        when(emailsService.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("unauthorized"));

        ResendDailyNotificationSender sender = new ResendDailyNotificationSender(resendClient, properties);
        DailyNotificationRequestedEvent event = buildEvent();

        assertThatThrownBy(() -> sender.send(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run-2026-08-23")
                .hasMessageContaining("unauthorized");
    }

    @Test
    void buildRequestUsesConfiguredFromAndToAddresses() {
        ResendDailyNotificationSender sender = new ResendDailyNotificationSender(resendClient, properties);
        CreateEmailOptions request = sender.buildRequest(buildEvent());

        assertThat(request.getFrom()).contains("no-reply@example.com");
        assertThat(request.getTo().get(0)).isEqualTo("you@example.com");
        assertThat(request.getSubject()).contains("2026-08-23");
    }

    private ResendProperties buildProperties() {
        ResendProperties properties = new ResendProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");
        properties.setFromEmail("no-reply@example.com");
        properties.setFromName("Job Scraper");
        properties.setToEmail("you@example.com");
        properties.setSubjectPrefix("Job Scraper Daily Summary");
        return properties;
    }

    private DailyNotificationRequestedEvent buildEvent() {
        return new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                5,
                14,
                6,
                2,
                List.of("oracle timeout"));
    }
}


