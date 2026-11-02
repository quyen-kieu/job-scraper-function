package com.jobscraper.application.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of {@link DailyNotificationPublisher} using Kafka.
 *
 * <p>Publishes to the {@code notifications.daily} topic keyed by {@code runId}, per the
 * README's topic design table.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaDailyNotificationPublisher implements DailyNotificationPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 30;


    private final Producer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public KafkaDailyNotificationPublisher(
            Producer<String, String> producer,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes synchronously (waits for the broker ack) so a failed send propagates to the
     * caller and the triggering {@code job.scrape.completed} offset is not committed.
     */
    @Override
    public void publish(DailyNotificationRequestedEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize daily notification event", ex);
        }

        ProducerRecord<String, String> record =
                new ProducerRecord<>(kafkaProperties.notificationsDailyTopic(), event.runId(), payload);
        try {
            producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing daily notification runId=" + event.runId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to publish daily notification runId=" + event.runId(), ex);
        }
    }
}

