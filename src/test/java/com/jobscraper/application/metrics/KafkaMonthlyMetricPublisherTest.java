package com.jobscraper.application.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class KafkaMonthlyMetricPublisherTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private Producer<String, String> producer;
    private KafkaProperties kafkaProperties;
    private ObjectMapper objectMapper;
    private KafkaMonthlyMetricPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setEnabled(true);
        kafkaProperties.setBootstrapServers(kafka.getBootstrapServers());
        kafkaProperties.setMetricsMonthlyTopic("job.metrics.monthly");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        producer = new KafkaProducer<>(producerProps);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new KafkaMonthlyMetricPublisher(producer, kafkaProperties, objectMapper);
    }

    @Test
    void shouldPublishMonthlyMetricKeyedByCompanyAndMonth() throws Exception {
        MonthlyMetricPublishedEvent event = new MonthlyMetricPublishedEvent(
                "mckesson",
                "2026-08",
                "Data Architect",
                "Lead",
                4,
                4,
                Instant.now()
        );

        publisher.publish(event);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("job.metrics.monthly"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThan(0);

            for (ConsumerRecord<String, String> record : records) {
                assertThat(record.key()).isEqualTo("mckesson:2026-08");
                MonthlyMetricPublishedEvent consumed = objectMapper.readValue(record.value(), MonthlyMetricPublishedEvent.class);
                assertThat(consumed.companyId()).isEqualTo("mckesson");
                assertThat(consumed.month()).isEqualTo("2026-08");
                assertThat(consumed.postingCount()).isEqualTo(4);
            }
        }
    }
}

