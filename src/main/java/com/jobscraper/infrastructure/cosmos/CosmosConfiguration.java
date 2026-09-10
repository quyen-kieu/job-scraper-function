package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.jobscraper.configuration.CosmosProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Azure Cosmos DB dependencies.
 *
 * <p>Beans are only created when {@code azure.cosmos.enabled=true}. Endpoint and key are
 * resolved exclusively from {@link CosmosProperties}, never hardcoded.</p>
 *
 * <p>The two {@link CosmosContainer} beans are distinguished by bean name (the method name),
 * which Spring uses automatically to satisfy {@code @Qualifier("jobPostingsContainer")} and
 * {@code @Qualifier("jobObservationsContainer")} at injection sites.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosConfiguration {

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient(CosmosProperties properties) {
        return new CosmosClientBuilder()
                .endpoint(properties.endpoint())
                .key(properties.key())
                .buildClient();
    }

    @Bean
    public CosmosContainer jobPostingsContainer(CosmosClient client, CosmosProperties properties) {
        return client.getDatabase(properties.databaseName()).getContainer(properties.postingsContainer());
    }

    @Bean
    public CosmosContainer jobObservationsContainer(CosmosClient client, CosmosProperties properties) {
        return client.getDatabase(properties.databaseName()).getContainer(properties.observationsContainer());
    }

    @Bean
    public CosmosContainer monthlyMetricsContainer(CosmosClient client, CosmosProperties properties) {
        return client.getDatabase(properties.databaseName()).getContainer(properties.metricsContainer());
    }
}


