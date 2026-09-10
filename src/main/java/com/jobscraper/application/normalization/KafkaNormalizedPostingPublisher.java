package com.jobscraper.application.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link NormalizedPostingPublisher} using Kafka.
 *
 * <p>Publishes to the {@code job.postings.normalized} topic with {@code companyId:externalJobId}
 * as the key (i.e. {@link NormalizedJobPosting#id()}), matching the README's topic design and
 * preserving per-posting ordering.</p>
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaNormalizedPostingPublisher implements NormalizedPostingPublisher {

    private final Producer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public KafkaNormalizedPostingPublisher(
            Producer<String, String> producer,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(NormalizedJobPosting posting) {
        try {
            String payload = objectMapper.writeValueAsString(posting);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            kafkaProperties.postingsNormalizedTopic(),
                            posting.id(),
                            payload
                    );
            producer.send(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize normalized job posting", ex);
        }
    }
}

