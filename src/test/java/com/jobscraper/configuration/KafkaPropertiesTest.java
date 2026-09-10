package com.jobscraper.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void usesSafeDefaultsWhenKafkaSettingsAreNotProvided() {
        contextRunner.run(context -> {
            KafkaProperties properties = context.getBean(KafkaProperties.class);

            assertThat(properties.enabled()).isFalse();
            assertThat(properties.bootstrapServers()).isBlank();
            assertThat(properties.securityProtocol()).isEqualTo("SASL_SSL");
            assertThat(properties.saslMechanism()).isEqualTo("PLAIN");
            assertThat(properties.scrapeCommandsTopic()).isEqualTo("job.scrape.commands");
            assertThat(properties.metricsMonthlyTopic()).isEqualTo("job.metrics.monthly");
        });
    }

    @Test
    void shouldDefaultPostingsNormalizedTopic() {
        contextRunner.run(context -> {
            KafkaProperties properties = context.getBean(KafkaProperties.class);

            assertThat(properties.postingsNormalizedTopic()).isEqualTo("job.postings.normalized");
        });
    }

    @Test
    void bindsKafkaEnvironmentStyleSettings() {
        contextRunner
                .withPropertyValues(
                        "kafka.enabled=true",
                        "kafka.bootstrap-servers=pkc-abcde.region.provider.confluent.cloud:9092",
                        "kafka.security-protocol=SASL_SSL",
                        "kafka.sasl-mechanism=PLAIN",
                        "kafka.username=my-api-key",
                        "kafka.password=my-api-secret",
                        "kafka.scrape-commands-topic=job.scrape.commands",
                        "kafka.metrics-monthly-topic=job.metrics.monthly")
                .run(context -> {
                    KafkaProperties properties = context.getBean(KafkaProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.bootstrapServers()).isEqualTo("pkc-abcde.region.provider.confluent.cloud:9092");
                    assertThat(properties.securityProtocol()).isEqualTo("SASL_SSL");
                    assertThat(properties.saslMechanism()).isEqualTo("PLAIN");
                    assertThat(properties.username()).isEqualTo("my-api-key");
                    assertThat(properties.password()).isEqualTo("my-api-secret");
                    assertThat(properties.scrapeCommandsTopic()).isEqualTo("job.scrape.commands");
                    assertThat(properties.metricsMonthlyTopic()).isEqualTo("job.metrics.monthly");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KafkaProperties.class)
    static class TestConfiguration {
    }
}
