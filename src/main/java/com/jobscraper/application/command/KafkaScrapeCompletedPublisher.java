package com.jobscraper.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import com.jobscraper.infrastructure.kafka.KafkaSendAcknowledgement;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link ScrapeCompletedPublisher} using Kafka.
 *
 * <p>Publishes to the {@code job.scrape.completed} topic with {@code companyId} as the key,
 * preserving per-company ordering and enabling parallel consumption.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaScrapeCompletedPublisher implements ScrapeCompletedPublisher {

    private final Producer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public KafkaScrapeCompletedPublisher(
            Producer<String, String> producer,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ScrapeCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            long bytes = (long) payload.getBytes(StandardCharsets.UTF_8).length
                    + event.companyId().getBytes(StandardCharsets.UTF_8).length;
            if (bytes >= kafkaProperties.maxRequestSize()) {
                throw new IllegalStateException("Scrape completion event for " + event.companyId()
                        + " is too large for the Kafka producer (" + bytes + " bytes; limit "
                        + kafkaProperties.maxRequestSize() + "). Reconciliation will not run.");
            }
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            kafkaProperties.scrapeCompletedTopic(),
                            event.companyId(),
                            payload
                    );
            KafkaSendAcknowledgement.send(producer, record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize scrape completed event", ex);
        }
    }
}
