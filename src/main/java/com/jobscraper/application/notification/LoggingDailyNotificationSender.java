package com.jobscraper.application.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Default sender that logs notifications when an outbound email provider is not yet wired.
 *
 * <p>Active when {@code resend.enabled} is {@code false} or unset, so local development and
 * tests never require a real Resend API key. See {@link ResendDailyNotificationSender} for
 * the production implementation.</p>
 */
@Component
@ConditionalOnProperty(prefix = "resend", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingDailyNotificationSender implements DailyNotificationSender {

    private static final Logger LOGGER = Logger.getLogger(LoggingDailyNotificationSender.class.getName());

    @Override
    public void send(DailyNotificationRequestedEvent event) {
        LOGGER.info(
                "Daily notification summary runId=" + event.runId()
                        + " date=" + event.summaryDate()
                        + " companiesProcessed=" + event.companiesProcessed()
                        + " newCount=" + event.newCount()
                        + " changedCount=" + event.changedCount()
                        + " inactiveCount=" + event.inactiveCount()
                        + " failures=" + event.failures().size()
        );
    }
}

