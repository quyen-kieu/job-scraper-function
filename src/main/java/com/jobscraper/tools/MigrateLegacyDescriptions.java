package com.jobscraper.tools;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosPatchItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Migrates legacy description fields without overwriting concurrent updates.
 * The default is a read-only dry run; pass --apply to patch documents.
 */
public final class MigrateLegacyDescriptions {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MigrateLegacyDescriptions() {
    }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !"--apply".equals(args[0]))) {
            throw new IllegalArgumentException("Expected no arguments (dry run) or --apply");
        }
        boolean apply = args.length == 1;
        try (CosmosClient client = new CosmosClientBuilder()
                .endpoint(requiredEnvironment("AZURE_COSMOS_ENDPOINT"))
                .key(requiredEnvironment("AZURE_COSMOS_KEY"))
                .gatewayMode()
                .buildClient()) {
            CosmosContainer container = client.getDatabase(requiredEnvironment("AZURE_COSMOS_DATABASE_NAME"))
                    .getContainer(requiredEnvironment("AZURE_COSMOS_POSTINGS_CONTAINER"));
            Result result = migrate(container, apply);
            System.out.printf("%s: examined=%d migrated=%d concurrent-changes=%d%n",
                    apply ? "Applied" : "Dry run", result.examined(), result.migrated(), result.conflicts());
            if (result.conflicts() > 0) {
                throw new IllegalStateException("Some documents changed concurrently; rerun the migration");
            }
        }
    }

    static Result migrate(CosmosContainer container, boolean apply) {
        int examined = 0;
        int migrated = 0;
        int conflicts = 0;
        var documents = container.queryItems(
                "SELECT c.id, c.companyId, c.description, c.descriptionPlainText, c._etag "
                        + "FROM c WHERE IS_DEFINED(c.description)",
                new CosmosQueryRequestOptions(), LegacyPosting.class);
        for (LegacyPosting document : documents) {
            examined++;
            if (document.id() == null || document.companyId() == null || document.etag() == null) {
                throw new IllegalStateException("Legacy posting is missing its ID, partition key, or ETag");
            }
            if (!apply) {
                continue;
            }
            if (migrateOne(container, document)) {
                migrated++;
            } else {
                conflicts++;
            }
        }
        return new Result(examined, migrated, conflicts);
    }

    static boolean migrateOne(CosmosContainer container, LegacyPosting document) {
        CosmosPatchOperations operations = CosmosPatchOperations.create();
        if (document.descriptionPlainText() == null && document.description() != null) {
            operations.set("/descriptionPlainText", document.description());
        }
        operations.remove("/description");
        CosmosPatchItemRequestOptions options = new CosmosPatchItemRequestOptions();
        options.setIfMatchETag(document.etag());
        try {
            options.setFilterPredicate("FROM c WHERE c._etag = " + JSON.writeValueAsString(document.etag()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot construct migration concurrency condition", e);
        }
        try {
            container.patchItem(document.id(), new PartitionKey(document.companyId()),
                    operations, options, Object.class);
            return true;
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == 412) {
                return false;
            }
            throw ex;
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }

    public record LegacyPosting(
            String id,
            String companyId,
            String description,
            String descriptionPlainText,
            @JsonProperty("_etag") String etag) {
    }

    record Result(int examined, int migrated, int conflicts) {
    }
}
