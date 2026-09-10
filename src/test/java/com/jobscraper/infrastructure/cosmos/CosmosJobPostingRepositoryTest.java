package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.job.NormalizedJobPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmosJobPostingRepositoryTest {

    @Mock
    private CosmosContainer container;

    @Mock
    private CosmosItemResponse<NormalizedJobPosting> response;

    @Test
    void shouldUpsertUsingCompanyIdPartitionKey() {
        when(container.upsertItem(any(NormalizedJobPosting.class), any(PartitionKey.class), any(CosmosItemRequestOptions.class)))
                .thenReturn(response);

        CosmosJobPostingRepository repository = new CosmosJobPostingRepository(container);
        NormalizedJobPosting posting = buildPosting();

        repository.upsert(posting);

        verify(container).upsertItem(
                eq(posting),
                eq(new PartitionKey("mckesson")),
                any(CosmosItemRequestOptions.class));
    }

    @Test
    void shouldUpsertSameIdTwiceWithoutCreatingDuplicate() {
        when(container.upsertItem(any(NormalizedJobPosting.class), any(PartitionKey.class), any(CosmosItemRequestOptions.class)))
                .thenReturn(response);

        CosmosJobPostingRepository repository = new CosmosJobPostingRepository(container);
        NormalizedJobPosting posting = buildPosting();

        repository.upsert(posting);
        repository.upsert(posting);

        // Idempotency contract: upsertItem is called each time with the SAME deterministic id,
        // relying on Cosmos DB's upsert semantics (not create) to avoid duplicates.
        verify(container, times(2)).upsertItem(
                eq(posting),
                eq(new PartitionKey("mckesson")),
                any(CosmosItemRequestOptions.class));
    }

    private NormalizedJobPosting buildPosting() {
        Instant now = Instant.now();
        return new NormalizedJobPosting(
                "mckesson:99215825472", "mckesson", "99215825472",
                "Lead Data Architect (Healthcare)", "Data Architect", "Lead",
                "Irving, TX", LocalDate.of(2026, 8, 14), now, now, true,
                "sha256:abc123", 1
        );
    }
}

