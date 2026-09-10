package com.jobscraper.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaScrapeCommandPublisherTest {

    @Test
    void publishesCommandToConfiguredTopicUsingCompanyIdAsMessageKey() {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setScrapeCommandsTopic("job.scrape.commands");

        KafkaScrapeCommandPublisher publisher =
                new KafkaScrapeCommandPublisher(
                        producer,
                        kafkaProperties,
                        new ObjectMapper().findAndRegisterModules());

        ScrapeCompanyCommand command =
                new ScrapeCompanyCommand("run-123", "mckesson", Instant.parse("2026-08-16T12:00:00Z"));

        publisher.publish(command);

        @SuppressWarnings("unchecked")
        var recordCaptor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(recordCaptor.capture());

        ProducerRecord<String, String> record = (ProducerRecord<String, String>) recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("job.scrape.commands");
        assertThat(record.key()).isEqualTo("mckesson");
        assertThat(record.value()).contains("run-123").contains("mckesson");
    }

    @Test
    void throwsMeaningfulErrorWhenPayloadSerializationFails() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);

        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {
                });

        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setScrapeCommandsTopic("job.scrape.commands");

        KafkaScrapeCommandPublisher publisher =
                new KafkaScrapeCommandPublisher(producer, kafkaProperties, objectMapper);

        ScrapeCompanyCommand command =
                new ScrapeCompanyCommand("run-123", "mckesson", Instant.parse("2026-08-16T12:00:00Z"));

        assertThatThrownBy(() -> publisher.publish(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serialize");
    }
}

