package com.jobscraper.tools;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Read-only lag check for the scraper pipeline during a storage cutover.
 */
public final class KafkaDrainCheck {
    private KafkaDrainCheck() {
    }

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        config.put("bootstrap.servers", required("KAFKA_BOOTSTRAP_SERVERS"));
        config.put("security.protocol", required("KAFKA_SECURITY_PROTOCOL"));
        config.put(SaslConfigs.SASL_MECHANISM, required("KAFKA_SASL_MECHANISM"));
        config.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                        + escape(required("KAFKA_USERNAME")) + "\" password=\""
                        + escape(required("KAFKA_PASSWORD")) + "\";");
        config.put("request.timeout.ms", "15000");
        config.put("default.api.timeout.ms", "30000");
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put(required("KAFKA_SCRAPER_CONSUMER_GROUP"), required("KAFKA_SCRAPE_COMMANDS_TOPIC"));
        groups.put(required("KAFKA_NORMALIZER_CONSUMER_GROUP"), required("KAFKA_SCRAPE_COMPLETED_TOPIC"));
        groups.put(required("KAFKA_PERSISTENCE_CONSUMER_GROUP"), required("KAFKA_POSTINGS_NORMALIZED_TOPIC"));
        boolean drained = true;
        try (Admin admin = Admin.create(config)) {
            for (var entry : groups.entrySet()) {
                var description = admin.describeTopics(List.of(entry.getValue())).allTopicNames()
                        .get(30, TimeUnit.SECONDS).get(entry.getValue());
                Map<TopicPartition, OffsetSpec> requests = new LinkedHashMap<>();
                description.partitions().forEach(partition ->
                        requests.put(new TopicPartition(entry.getValue(), partition.partition()), OffsetSpec.latest()));
                if (requests.isEmpty()) {
                    throw new IllegalStateException("Topic has no partitions: " + entry.getValue());
                }
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                        admin.listOffsets(requests).all().get(30, TimeUnit.SECONDS);
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(entry.getKey())
                        .partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);
                for (TopicPartition partition : requests.keySet()) {
                    OffsetAndMetadata offset = committed.get(partition);
                    if (offset == null) {
                        throw new IllegalStateException("Consumer group has no committed offset for " + partition);
                    }
                    long lag = ends.get(partition).offset() - offset.offset();
                    if (lag < 0) {
                        throw new IllegalStateException("Consumer offset is ahead of broker end for " + partition);
                    }
                    System.out.printf("%s partition=%d lag=%d%n", entry.getValue(), partition.partition(), lag);
                    drained &= lag == 0;
                }
            }
        }
        if (!drained) {
            throw new IllegalStateException("Kafka pipeline is not drained");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
