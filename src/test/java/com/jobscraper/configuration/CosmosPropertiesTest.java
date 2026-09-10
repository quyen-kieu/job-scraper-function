package com.jobscraper.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CosmosPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldDefaultToDisabled() {
        contextRunner.run(context -> {
            CosmosProperties properties = context.getBean(CosmosProperties.class);

            assertThat(properties.enabled()).isFalse();
            assertThat(properties.endpoint()).isBlank();
            assertThat(properties.key()).isBlank();
        });
    }

    @Test
    void shouldDefaultContainerNames() {
        contextRunner.run(context -> {
            CosmosProperties properties = context.getBean(CosmosProperties.class);

            assertThat(properties.databaseName()).isEqualTo("job-scraper");
            assertThat(properties.postingsContainer()).isEqualTo("job-postings");
            assertThat(properties.observationsContainer()).isEqualTo("job-observations");
            assertThat(properties.metricsContainer()).isEqualTo("monthly-metrics");
        });
    }

    @Test
    void shouldNullCoalesceBlankOverrides() {
        CosmosProperties properties = new CosmosProperties();

        properties.setDatabaseName(null);
        properties.setPostingsContainer(null);
        properties.setObservationsContainer(null);
        properties.setMetricsContainer(null);
        properties.setEndpoint(null);
        properties.setKey(null);

        assertThat(properties.databaseName()).isEqualTo("job-scraper");
        assertThat(properties.postingsContainer()).isEqualTo("job-postings");
        assertThat(properties.observationsContainer()).isEqualTo("job-observations");
        assertThat(properties.metricsContainer()).isEqualTo("monthly-metrics");
        assertThat(properties.endpoint()).isEmpty();
        assertThat(properties.key()).isEmpty();
    }

    @Test
    void shouldBindEnvironmentStyleSettings() {
        contextRunner
                .withPropertyValues(
                        "azure.cosmos.enabled=true",
                        "azure.cosmos.endpoint=https://example.documents.azure.com:443/",
                        "azure.cosmos.key=my-secret-key",
                        "azure.cosmos.database-name=custom-db")
                .run(context -> {
                    CosmosProperties properties = context.getBean(CosmosProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.endpoint()).isEqualTo("https://example.documents.azure.com:443/");
                    assertThat(properties.key()).isEqualTo("my-secret-key");
                    assertThat(properties.databaseName()).isEqualTo("custom-db");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CosmosProperties.class)
    static class TestConfiguration {
    }
}

