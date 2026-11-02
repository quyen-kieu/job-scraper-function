package com.jobscraper.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * Application service for {@code notifications.daily} consumption.
 */
@Service
public class DailyNotificationHandler {

    private static final Logger LOGGER = Logger.getLogger(DailyNotificationHandler.class.getName());

    private final ObjectMapper objectMapper;
    private final DailyNotificationSender sender;
    private final SentNotificationGuard sentNotificationGuard;

    public DailyNotificationHandler(
            ObjectMapper objectMapper, DailyNotificationSender sender, SentNotificationGuard sentNotificationGuard) {
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.sentNotificationGuard = sentNotificationGuard;
    }

    /**
     * Deserializes and sends one daily notification request.
     *
     * <p>Skips sending (but still returns the parsed event) if a notification for this event's
     * {@code runId} and {@code companyId} was already sent successfully, preventing duplicate emails
     * when Kafka redelivers a {@code notifications.daily} message. See {@link SentNotificationGuard}.
     * The company is part of the key because the daily timer publishes every company under one
     * shared {@code runId}, and each company gets its own email.</p>
     *
     * @param json raw JSON message
     * @return parsed event
     * @throws Exception deserialization or sender failure
     */
    public DailyNotificationRequestedEvent handle(String json) throws Exception {
        DailyNotificationRequestedEvent event = objectMapper.readValue(json, DailyNotificationRequestedEvent.class);
        String key = guardKey(event);

        if (sentNotificationGuard.alreadySent(key)) {
            LOGGER.info("Skipping duplicate daily notification send for " + key);
            return event;
        }

        sender.send(event);
        sentNotificationGuard.markSent(key);
        return event;
    }

    /**
     * Returns the de-duplication key: {@code runId} alone for legacy events without a company,
     * otherwise {@code runId:companyId}.
     */
    static String guardKey(DailyNotificationRequestedEvent event) {
        if (event.companyId() == null || event.companyId().isBlank()) {
            return event.runId();
        }
        return event.runId() + ":" + event.companyId();
    }
}



