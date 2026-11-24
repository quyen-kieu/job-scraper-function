package com.jobscraper.tools;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_COSMOS_HANDOFF", matches = "true")
class LiveCosmosLegacyMigrationTest {
    @Test
    void migratesOnlyTheTestDocumentAndRejectsStaleEtag() {
        try (var client = new CosmosClientBuilder()
                .endpoint(System.getenv("AZURE_COSMOS_ENDPOINT"))
                .key(System.getenv("AZURE_COSMOS_KEY"))
                .gatewayMode()
                .buildClient()) {
            var container = client.getDatabase(System.getenv("AZURE_COSMOS_DATABASE_NAME"))
                    .getContainer(System.getenv("AZURE_COSMOS_DESCRIPTION_HANDOFF_CONTAINER"));
            String id = "migration-verification-" + UUID.randomUUID();
            String company = "handoff-verification";
            var partition = new PartitionKey(company);
            container.createItem(Map.of("id", id, "companyId", company, "description", "Legacy text"),
                    partition, new CosmosItemRequestOptions());
            try {
                var stale = container.readItem(id, partition, MigrateLegacyDescriptions.LegacyPosting.class).getItem();
                container.patchItem(id, partition, CosmosPatchOperations.create().set("/testMarker", true), Object.class);
                assertThat(MigrateLegacyDescriptions.migrateOne(container, stale)).isFalse();
                var current = container.readItem(id, partition, MigrateLegacyDescriptions.LegacyPosting.class).getItem();
                assertThat(MigrateLegacyDescriptions.migrateOne(container, current)).isTrue();
                JsonNode result = container.readItem(id, partition, JsonNode.class).getItem();
                assertThat(result.has("description")).isFalse();
                assertThat(result.path("descriptionPlainText").asText()).isEqualTo("Legacy text");
                assertThat(result.path("testMarker").asBoolean()).isTrue();
            } finally {
                container.deleteItem(id, partition, new CosmosItemRequestOptions());
            }
        }
    }
}
