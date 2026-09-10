package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.jobscraper.domain.job.JobObservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmosJobObservationRepositoryTest {

    @Mock
    private CosmosContainer container;

    @Mock
    private CosmosItemResponse<JobObservation> response;

    @Test
    void shouldUpsertUsingDeterministicObservationId() {
        when(container.upsertItem(any(JobObservation.class), any(PartitionKey.class), any(CosmosItemRequestOptions.class)))
                .thenReturn(response);

        CosmosJobObservationRepository repository = new CosmosJobObservationRepository(container);

        LocalDate scrapeDate = LocalDate.of(2026, 8, 16);
        String expectedId = JobObservation.buildId("mckesson", scrapeDate, "99215825472");

        JobObservation observation = new JobObservation(
                expectedId, "mckesson", "99215825472", "run-123",
                Instant.now(), scrapeDate, LocalDate.of(2026, 8, 14),
                "Data Architect", "Lead", "sha256:abc123", 1
        );

        repository.upsert(observation);

        verify(container).upsertItem(
                eq(observation),
                eq(new PartitionKey("mckesson")),
                any(CosmosItemRequestOptions.class));
    }
}

