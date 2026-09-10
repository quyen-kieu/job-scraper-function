package com.jobscraper.application.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link DailyNotificationPublisher} using Kafka.
 *
 * <p>Publishes to the {@code notifications.daily} topic keyed by {@code runId}, per the
 * README's topic design table.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaDailyNotificationPublisher implements DailyNotificationPublisher {

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

    @Override
    public void publish(DailyNotificationRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(kafkaProperties.notificationsDailyTopic(), event.runId(), payload);
            producer.send(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize daily notification event", ex);
        }
    }
}

