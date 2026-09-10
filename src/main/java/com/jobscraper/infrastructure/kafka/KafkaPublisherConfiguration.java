package com.jobscraper.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.configuration.KafkaProperties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true")
public class KafkaPublisherConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean(name = "kafkaProducerProperties")
    public Properties kafkaProducerProperties(KafkaProperties kafkaProperties) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        properties.put("security.protocol", kafkaProperties.securityProtocol());
        properties.put(SaslConfigs.SASL_MECHANISM, kafkaProperties.saslMechanism());
        properties.put(SaslConfigs.SASL_JAAS_CONFIG, buildJaasConfig(kafkaProperties));

        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        return properties;
    }

    @Bean(destroyMethod = "close")
    public Producer<String, String> kafkaProducer(@Qualifier("kafkaProducerProperties") Properties kafkaProducerProperties) {
        return new KafkaProducer<>(kafkaProducerProperties);
    }

    private static String buildJaasConfig(KafkaProperties kafkaProperties) {
        return "org.apache.kafka.common.security.plain.PlainLoginModule required "
                + "username=\"" + escape(kafkaProperties.username()) + "\" "
                + "password=\"" + escape(kafkaProperties.password()) + "\";";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
