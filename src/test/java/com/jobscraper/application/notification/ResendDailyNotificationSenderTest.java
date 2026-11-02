package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;
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

    @Test
    void buildRequestListsEveryScrapedPostingInTextAndHtml() {
        ResendDailyNotificationSender sender = new ResendDailyNotificationSender(resendClient, properties);
        CreateEmailOptions request = sender.buildRequest(buildEventWithPostings());

        assertThat(request.getSubject())
                .isEqualTo("Job Scraper Daily Summary - mckesson - 2026-08-23 (2 postings, 1 new)");

        assertThat(request.getText())
                .contains("New postings (1):")
                .contains("Lead Data Architect | Irving, TX | posted 2026-08-14")
                .contains("https://careers.mckesson.com/en/job/irving/lead/733/1")
                .contains("Previously seen postings (1):")
                .contains("Analyst | Remote");

        assertThat(request.getHtml())
                .contains("<a href=\"https://careers.mckesson.com/en/job/irving/lead/733/1\">Lead Data Architect</a>")
                .contains("Irving, TX")
                .contains("2026-08-14");
    }

    @Test
    void buildRequestEscapesHtmlAndOnlyLinksHttpUrls() {
        DailyNotificationRequestedEvent event = new DailyNotificationRequestedEvent(
                "run-1", LocalDate.of(2026, 8, 23), 1, 1, 0, 0, List.of(), "mckesson",
                List.of(new NotificationPosting("9", "<script>x</script> & Co", "A \"B\"", null,
                        "javascript:alert(1)", PersistenceOutcome.NEW)));

        String html = new ResendDailyNotificationSender(resendClient, properties).buildRequest(event).getHtml();

        assertThat(html)
                .contains("&lt;script&gt;x&lt;/script&gt; &amp; Co")
                .contains("A &quot;B&quot;")
                .doesNotContain("<script>")
                .doesNotContain("javascript:");
    }

    private DailyNotificationRequestedEvent buildEventWithPostings() {
        return new DailyNotificationRequestedEvent(
                "run-2026-08-23",
                LocalDate.of(2026, 8, 23),
                1,
                1,
                0,
                0,
                List.of(),
                "mckesson",
                List.of(
                        new NotificationPosting("1", "Lead Data Architect", "Irving, TX",
                                LocalDate.of(2026, 8, 14), "https://careers.mckesson.com/en/job/irving/lead/733/1",
                                PersistenceOutcome.NEW),
                        new NotificationPosting("2", "Analyst", "Remote", null, null,
                                PersistenceOutcome.UNCHANGED)));
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


