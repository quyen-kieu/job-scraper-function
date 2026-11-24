package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.job.ContentHasher;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Stores immutable, temporary description handoffs in Cosmos DB. Raw responses are not archived.
 */
@Component
@ConditionalOnProperty(prefix = "raw-storage", name = "mode", havingValue = "cosmos")
public class CosmosDescriptionHandoffStorage implements RawScrapeStorage {

    private final CosmosContainer container;

    public CosmosDescriptionHandoffStorage(
            @Qualifier("descriptionHandoffContainer") CosmosContainer container) {
        this.container = container;
    }

    @Override
    public String writeSearchPage(String companyId, String runId, int pageNumber, String json) {
        return "not-stored://search/" + companyId + "/" + runId + "/" + pageNumber;
    }

    @Override
    public String writeJobDetail(String companyId, String runId, String externalJobId, String html) {
        return "not-stored://detail/" + companyId + "/" + runId + "/" + externalJobId;
    }

    @Override
    public String writeRunSummary(String companyId, String runId, String json) {
        return "not-stored://summary/" + companyId + "/" + runId;
    }

    @Override
    public String writeDescription(String companyId, String runId, String externalJobId, String source) {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(externalJobId, "externalJobId");
        Objects.requireNonNull(source, "source");
        String id = handoffId(runId, externalJobId, source);
        var handoff = new DescriptionHandoff(id, companyId, runId, externalJobId, source);
        container.upsertItem(handoff, new PartitionKey(companyId), new CosmosItemRequestOptions());
        return id;
    }

    @Override
    public String readDescription(String companyId, String externalJobId, String path) {
        if (path == null || !path.matches("handoff-[0-9a-f]{64}-[0-9a-f]{64}-[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Cosmos description handoff reference");
        }
        DescriptionHandoff handoff = container.readItem(
                path, new PartitionKey(companyId), DescriptionHandoff.class).getItem();
        if (handoff == null || !companyId.equals(handoff.companyId())
                || !externalJobId.equals(handoff.externalJobId())
                || handoff.runId() == null || handoff.source() == null
                || !path.equals(handoffId(handoff.runId(), externalJobId, handoff.source()))) {
            throw new IllegalStateException("Description handoff does not match its reference");
        }
        return handoff.source();
    }

    private static String handoffId(String runId, String externalJobId, String source) {
        return "handoff-" + hash(runId) + "-" + hash(externalJobId) + "-" + hash(source);
    }

    private static String hash(String value) {
        return ContentHasher.sha256(value).substring("sha256:".length());
    }

    public record DescriptionHandoff(
            String id, String companyId, String runId, String externalJobId, String source) {
    }
}
