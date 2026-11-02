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
        return CreateEmailOptions.builder()
                .from(from)
                .to(properties.toEmail())
                .subject(buildSubject(event))
                .text(DailyNotificationFormatter.toText(event))
                .html(DailyNotificationFormatter.toHtml(event))
                .build();
    }

    private String buildSubject(DailyNotificationRequestedEvent event) {
        StringBuilder subject = new StringBuilder(properties.subjectPrefix());
        if (event.companyId() != null && !event.companyId().isBlank()) {
            subject.append(" - ").append(event.companyId());
        }
        subject.append(" - ").append(event.summaryDate());
        if (!event.postings().isEmpty()) {
            subject.append(" (").append(event.postings().size()).append(" postings, ")
                    .append(event.newCount()).append(" new)");
        }
        return subject.toString();
    }
}

