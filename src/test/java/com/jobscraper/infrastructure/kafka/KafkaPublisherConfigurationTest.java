package com.jobscraper.infrastructure.kafka;

import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPublisherConfigurationTest {

    @Test
    void buildsProducerPropertiesWithRequiredSecurityConfiguration() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setEnabled(true);
        kafkaProperties.setBootstrapServers("pkc-abcde.region.provider.confluent.cloud:9092");
        kafkaProperties.setSecurityProtocol("SASL_SSL");
        kafkaProperties.setSaslMechanism("PLAIN");
        kafkaProperties.setUsername("api-key");
        kafkaProperties.setPassword("api-secret");

        KafkaPublisherConfiguration configuration = new KafkaPublisherConfiguration();

        Properties properties = configuration.kafkaProducerProperties(kafkaProperties);

        assertThat(properties.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo("pkc-abcde.region.provider.confluent.cloud:9092");
        assertThat(properties.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(properties.getProperty(SaslConfigs.SASL_MECHANISM)).isEqualTo("PLAIN");
        assertThat(properties.getProperty(SaslConfigs.SASL_JAAS_CONFIG))
                .contains("username=\"api-key\"")
                .contains("password=\"api-secret\"");

        assertThat(properties.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
        assertThat(properties.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(3);
    }
}

