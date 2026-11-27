package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosItemSerializer;
import com.jobscraper.domain.job.NormalizedJobPosting;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preserves timestamps used by the reconciliation ordering guard without changing Kafka payloads.
 */
final class PreciseCosmosItemSerializer extends CosmosItemSerializer {
    @Override
    public <T> Map<String, Object> serialize(T item) {
        Map<String, Object> document = DEFAULT_SERIALIZER.serialize(item);
        if (item instanceof CompanyScrapeState state) {
            document = new LinkedHashMap<>(document);
            document.put("watermarkAt", timestamp(state.watermarkAt()));
            document.put("lastSuccessfulReconciliationAt", timestamp(state.lastSuccessfulReconciliationAt()));
        } else if (item instanceof NormalizedJobPosting posting) {
            document = new LinkedHashMap<>(document);
            document.put("firstSeenAt", timestamp(posting.firstSeenAt()));
            document.put("lastSeenAt", timestamp(posting.lastSeenAt()));
        }
        return document;
    }

    @Override
    public <T> T deserialize(Map<String, Object> document, Class<T> type) {
        return DEFAULT_SERIALIZER.deserialize(document, type);
    }

    private static String timestamp(Instant value) {
        return value == null ? null : value.toString();
    }
}
