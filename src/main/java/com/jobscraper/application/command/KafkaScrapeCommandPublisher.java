package com.jobscraper.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaScrapeCommandPublisher implements ScrapeCommandPublisher {

    private final Producer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public KafkaScrapeCommandPublisher(
            Producer<String, String> producer,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ScrapeCompanyCommand command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(kafkaProperties.scrapeCommandsTopic(), command.companyId(), payload);
            producer.send(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize scrape command", ex);
        }
    }
}

