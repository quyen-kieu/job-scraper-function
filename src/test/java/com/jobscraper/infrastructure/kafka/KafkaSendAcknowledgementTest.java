package com.jobscraper.infrastructure.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaSendAcknowledgementTest {
    @Test
    void propagatesBrokerFailureInsteadOfAcknowledgingTheInput() {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        var record = new ProducerRecord<String, String>("job.scrape.completed", "company", "payload");
        CompletableFuture<RecordMetadata> failure = new CompletableFuture<>();
        failure.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(producer.send(record)).thenReturn(failure);

        assertThatThrownBy(() -> KafkaSendAcknowledgement.send(producer, record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("job.scrape.completed")
                .hasRootCauseMessage("broker unavailable");
    }

    @Test
    void returnsAfterBrokerAcknowledgement() {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        var record = new ProducerRecord<String, String>("job.postings.normalized", "id", "payload");
        when(producer.send(record)).thenReturn(CompletableFuture.completedFuture(null));
        KafkaSendAcknowledgement.send(producer, record);
    }
}
