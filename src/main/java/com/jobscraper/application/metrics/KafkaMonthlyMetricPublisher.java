package com.jobscraper.application.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of {@link MonthlyMetricPublisher}.
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaMonthlyMetricPublisher implements MonthlyMetricPublisher {

    private final Producer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public KafkaMonthlyMetricPublisher(
            Producer<String, String> producer,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(MonthlyMetricPublishedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.companyId() + ":" + event.month();
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(kafkaProperties.metricsMonthlyTopic(), key, payload);
            producer.send(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize monthly metric event", ex);
        }
    }
}

