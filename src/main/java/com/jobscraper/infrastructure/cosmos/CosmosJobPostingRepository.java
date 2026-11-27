package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchItemRequestOptions;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "azure.cosmos", name = "enabled", havingValue = "true")
public class CosmosJobPostingRepository implements JobPostingRepository {
    private static final int MAX_CONFLICT_RETRIES = 30;
    private final CosmosContainer container;

    public CosmosJobPostingRepository(@Qualifier("jobPostingsContainer") CosmosContainer container) {
        this.container = container;
    }

    @Override
    public void upsert(NormalizedJobPosting posting) {
        upsertIfCurrent(posting, false);
    }

    @Override
    public boolean upsertIfCurrent(NormalizedJobPosting posting, boolean preserveDescription) {
        PartitionKey partition = new PartitionKey(posting.companyId());
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            Versioned<CompanyScrapeState> state = getOrCreateState(posting.companyId());
            if (compare(posting.lastSeenAt(), posting.scrapeRunId(),
                    state.item().watermarkAt(), state.item().watermarkRunId()) < 0) {
                return false;
            }
            Versioned<NormalizedJobPosting> previous = read(posting.id(), partition, NormalizedJobPosting.class);
            if (previous != null && compare(posting.lastSeenAt(), posting.scrapeRunId(),
                    previous.item().lastSeenAt(), previous.item().scrapeRunId()) < 0) {
                return false;
            }
            NormalizedJobPosting next = posting;
            if (previous != null) {
                if (previous.item().firstSeenAt().isBefore(next.firstSeenAt())) {
                    next = next.withFirstSeenAt(previous.item().firstSeenAt());
                }
                if (preserveDescription) {
                    next = next.withPreservedDetails(previous.item());
                }
            }
            CosmosBatch batch = CosmosBatch.createCosmosBatch(partition);
            batch.replaceItemOperation(CompanyScrapeState.ID, state.item(), ifMatch(state.etag()));
            if (previous == null) {
                batch.createItemOperation(next);
            } else {
                batch.replaceItemOperation(next.id(), next, ifMatch(previous.etag()));
            }
            if (execute(batch)) {
                return true;
            }
        }
        throw new IllegalStateException("Too many concurrent posting updates for " + posting.id());
    }

    @Override
    public Optional<NormalizedJobPosting> findById(String id, String companyId) {
        Versioned<NormalizedJobPosting> item = read(id, new PartitionKey(companyId), NormalizedJobPosting.class);
        return item == null ? Optional.empty() : Optional.of(item.item());
    }

    @Override
    public Optional<Instant> lastSuccessfulReconciliationAt(String companyId) {
        Versioned<CompanyScrapeState> state = read(CompanyScrapeState.ID,
                new PartitionKey(companyId), CompanyScrapeState.class);
        return state == null ? Optional.empty() : Optional.ofNullable(state.item().lastSuccessfulReconciliationAt());
    }

    @Override
    public ReconciliationResult reconcile(String companyId, String runId, Instant startedAt, Set<String> seenIds) {
        if (seenIds.isEmpty()) {
            return ReconciliationResult.skipped();
        }
        if (!beginReconciliation(companyId, runId, startedAt)) {
            return ReconciliationResult.skipped();
        }
        PartitionKey partition = new PartitionKey(companyId);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions().setPartitionKey(partition);
        List<String> activeIds = new ArrayList<>();
        container.queryItems("SELECT * FROM c WHERE c.isActive = true", options, NormalizedJobPosting.class)
                .forEach(posting -> {
                    if (!seenIds.contains(posting.externalJobId())) {
                        activeIds.add(posting.id());
                    }
                });
        for (String id : activeIds) {
            if (!markInactive(companyId, id, runId, startedAt)) {
                return ReconciliationResult.skipped();
            }
        }
        return finishReconciliation(companyId, runId, startedAt);
    }

    private boolean beginReconciliation(String companyId, String runId, Instant startedAt) {
        PartitionKey partition = new PartitionKey(companyId);
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            Versioned<CompanyScrapeState> state = getOrCreateState(companyId);
            int order = compare(startedAt, runId, state.item().watermarkAt(), state.item().watermarkRunId());
            if (order < 0) {
                return false;
            }
            if (order == 0) {
                return true;
            }
            try {
                container.replaceItem(state.item().begin(startedAt, runId), CompanyScrapeState.ID,
                        partition, new CosmosItemRequestOptions().setIfMatchETag(state.etag()));
                return true;
            } catch (CosmosException ex) {
                if (ex.getStatusCode() != 412) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Too many concurrent reconciliation starts for " + companyId);
    }

    private boolean markInactive(String companyId, String id, String runId, Instant startedAt) {
        PartitionKey partition = new PartitionKey(companyId);
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            Versioned<CompanyScrapeState> state = getOrCreateState(companyId);
            if (compare(startedAt, runId, state.item().watermarkAt(), state.item().watermarkRunId()) != 0) {
                return false;
            }
            Versioned<NormalizedJobPosting> posting = read(id, partition, NormalizedJobPosting.class);
            if (posting == null || !posting.item().isActive()
                    || compare(posting.item().lastSeenAt(), posting.item().scrapeRunId(), startedAt, runId) > 0) {
                return true;
            }
            CosmosBatch batch = CosmosBatch.createCosmosBatch(partition);
            batch.replaceItemOperation(CompanyScrapeState.ID, state.item().countInactive(), ifMatch(state.etag()));
            batch.replaceItemOperation(id, posting.item().withActive(false), ifMatch(posting.etag()));
            if (execute(batch)) {
                return true;
            }
        }
        throw new IllegalStateException("Too many concurrent reconciliation updates for " + id);
    }

    private ReconciliationResult finishReconciliation(String companyId, String runId, Instant startedAt) {
        PartitionKey partition = new PartitionKey(companyId);
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            Versioned<CompanyScrapeState> state = getOrCreateState(companyId);
            if (compare(startedAt, runId, state.item().watermarkAt(), state.item().watermarkRunId()) != 0) {
                return ReconciliationResult.skipped();
            }
            try {
                container.replaceItem(state.item().complete(), CompanyScrapeState.ID, partition,
                        new CosmosItemRequestOptions().setIfMatchETag(state.etag()));
                return new ReconciliationResult(true, state.item().inactiveCount());
            } catch (CosmosException ex) {
                if (ex.getStatusCode() != 412) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Too many concurrent reconciliation completions for " + companyId);
    }

    private Versioned<CompanyScrapeState> getOrCreateState(String companyId) {
        PartitionKey partition = new PartitionKey(companyId);
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            Versioned<CompanyScrapeState> existing = read(CompanyScrapeState.ID, partition, CompanyScrapeState.class);
            if (existing != null) {
                return existing;
            }
            try {
                CosmosItemResponse<CompanyScrapeState> created = container.createItem(
                        CompanyScrapeState.empty(companyId), partition, new CosmosItemRequestOptions());
                return new Versioned<>(created.getItem(), created.getETag());
            } catch (CosmosException ex) {
                if (ex.getStatusCode() != 409) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Unable to create scrape control document for " + companyId);
    }

    private <T> Versioned<T> read(String id, PartitionKey partition, Class<T> type) {
        try {
            CosmosItemResponse<T> response = container.readItem(id, partition, type);
            return new Versioned<>(response.getItem(), response.getETag());
        } catch (CosmosException ex) {
            if (ex.getStatusCode() == 404) {
                return null;
            }
            throw ex;
        }
    }

    private boolean execute(CosmosBatch batch) {
        CosmosBatchResponse response = container.executeCosmosBatch(batch);
        if (response.isSuccessStatusCode()) {
            return true;
        }
        if (response.getStatusCode() == 409 || response.getStatusCode() == 412
                || response.getResults().stream().anyMatch(result ->
                result.getStatusCode() == 409 || result.getStatusCode() == 412)) {
            return false;
        }
        throw new IllegalStateException("Cosmos transactional batch failed with HTTP " + response.getStatusCode());
    }

    private static CosmosBatchItemRequestOptions ifMatch(String etag) {
        return new CosmosBatchItemRequestOptions().setIfMatchETag(etag);
    }

    private static int compare(Instant a, String aRun, Instant b, String bRun) {
        if (b == null) {
            return 1;
        }
        int time = a.compareTo(b);
        return time != 0 ? time : java.util.Objects.toString(aRun, "").compareTo(java.util.Objects.toString(bRun, ""));
    }

    private record Versioned<T>(T item, String etag) {
    }
}
