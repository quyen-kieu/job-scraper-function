package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_COSMOS_HANDOFF", matches = "true")
class LiveCosmosDescriptionHandoffTest {
    @Test
    void roundTripsARealHandoffAndRemovesTheTestItem() {
        try (var client = new CosmosClientBuilder()
                .endpoint(System.getenv("AZURE_COSMOS_ENDPOINT"))
                .key(System.getenv("AZURE_COSMOS_KEY"))
                .gatewayMode()
                .buildClient()) {
            var container = client.getDatabase(System.getenv("AZURE_COSMOS_DATABASE_NAME"))
                    .getContainer(System.getenv("AZURE_COSMOS_DESCRIPTION_HANDOFF_CONTAINER"));
            assertThat(container.read().getProperties().getDefaultTimeToLiveInSeconds()).isEqualTo(2_592_000);
            var storage = new CosmosDescriptionHandoffStorage(container);
            String company = "handoff-verification";
            String run = UUID.randomUUID().toString();
            String job = UUID.randomUUID().toString();
            String source = "<p>Verification caf\u00e9</p>";
            String id = storage.writeDescription(company, run, job, source);
            try {
                assertThat(storage.readDescription(company, job, id)).isEqualTo(source);
            } finally {
                container.deleteItem(id, new PartitionKey(company), new CosmosItemRequestOptions());
            }
        }
    }
}
