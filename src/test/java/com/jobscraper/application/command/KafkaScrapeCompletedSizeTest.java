package com.jobscraper.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class KafkaScrapeCompletedSizeTest {
    @Test
    @SuppressWarnings("unchecked")
    void rejectsOversizedCompletionBeforePublishing() {
        Producer<String, String> producer = mock(Producer.class);
        KafkaProperties properties = new KafkaProperties();
        properties.setMaxRequestSize(64);
        KafkaScrapeCompletedPublisher publisher = new KafkaScrapeCompletedPublisher(
                producer, properties, new ObjectMapper().findAndRegisterModules());
        ScrapeCompletedEvent event = new ScrapeCompletedEvent("run", "company", Instant.EPOCH,
                Instant.EPOCH, 1, 0, List.of(), List.of());

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too large").hasMessageContaining("Reconciliation will not run");
        verifyNoInteractions(producer);
    }
}
