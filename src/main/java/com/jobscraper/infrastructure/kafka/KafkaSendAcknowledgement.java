package com.jobscraper.infrastructure.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class KafkaSendAcknowledgement {
    private static final long TIMEOUT_SECONDS = 30;

    private KafkaSendAcknowledgement() {
    }

    public static void send(Producer<String, String> producer, ProducerRecord<String, String> record) {
        try {
            producer.send(record).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + record.topic(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish to " + record.topic(), e);
        }
    }
}
