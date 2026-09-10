package com.jobscraper.application.notification;

import com.jobscraper.configuration.ResendProperties;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Sends daily notification summaries using the official Resend Java SDK.
 *
 * <p>Active only when {@code resend.enabled=true}; otherwise {@link LoggingDailyNotificationSender}
 * is used as a fallback so local development never requires a real Resend API key.</p>
 */
@Component
@ConditionalOnProperty(prefix = "resend", name = "enabled", havingValue = "true")
public class ResendDailyNotificationSender implements DailyNotificationSender {

    private static final Logger LOGGER = Logger.getLogger(ResendDailyNotificationSender.class.getName());

    private final Resend resendClient;
    private final ResendProperties properties;

    public ResendDailyNotificationSender(Resend resendClient, ResendProperties properties) {
        this.resendClient = resendClient;
        this.properties = properties;
    }

    @Override
    public void send(DailyNotificationRequestedEvent event) throws Exception {
        CreateEmailOptions request = buildRequest(event);

        try {
            CreateEmailResponse response = resendClient.emails().send(request);
            LOGGER.info(
                    "Sent daily notification email runId=" + event.runId()
                            + " summaryDate=" + event.summaryDate()
                            + " resendId=" + response.getId());
        } catch (ResendException e) {
            throw new IllegalStateException(
                    "Resend request failed for runId=" + event.runId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the Resend {@link CreateEmailOptions} payload for one daily notification event.
     *
     * <p>Package-private so unit tests can verify the constructed payload without making
     * a real HTTP call.</p>
     */
    CreateEmailOptions buildRequest(DailyNotificationRequestedEvent event) {
        String from = properties.fromName() + " <" + properties.fromEmail() + ">";
        String subject = properties.subjectPrefix() + " - " + event.summaryDate();
        return CreateEmailOptions.builder()
                .from(from)
                .to(properties.toEmail())
                .subject(subject)
                .text(buildBody(event))
                .build();
    }

    private String buildBody(DailyNotificationRequestedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("Run ID: ").append(event.runId()).append(System.lineSeparator());
        body.append("Summary date: ").append(event.summaryDate()).append(System.lineSeparator());
        body.append("Companies processed: ").append(event.companiesProcessed()).append(System.lineSeparator());
        body.append("New postings: ").append(event.newCount()).append(System.lineSeparator());
        body.append("Changed postings: ").append(event.changedCount()).append(System.lineSeparator());
        body.append("Inactive postings: ").append(event.inactiveCount()).append(System.lineSeparator());
        body.append("Failures (").append(event.failures().size()).append("):").append(System.lineSeparator());
        for (String failure : event.failures()) {
            body.append("  - ").append(failure).append(System.lineSeparator());
        }
        return body.toString();
    }
}

