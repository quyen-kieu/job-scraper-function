package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmosDescriptionHandoffStorageTest {
    @Test
    void storesAndReadsAnImmutableContentAddressedHandoff() {
        CosmosContainer container = mock(CosmosContainer.class);
        CosmosDescriptionHandoffStorage storage = new CosmosDescriptionHandoffStorage(container);
        String source = "<p>Build caf\u00e9 systems</p>";

        String id = storage.writeDescription("mckesson", "run-1", "JR1", source);
        var captor = ArgumentCaptor.forClass(CosmosDescriptionHandoffStorage.DescriptionHandoff.class);
        verify(container).upsertItem(captor.capture(), eq(new PartitionKey("mckesson")),
                any(CosmosItemRequestOptions.class));
        var handoff = captor.getValue();
        assertThat(handoff.id()).isEqualTo(id);
        assertThat(handoff.source()).isEqualTo(source);

        @SuppressWarnings("unchecked")
        CosmosItemResponse<CosmosDescriptionHandoffStorage.DescriptionHandoff> response = mock(CosmosItemResponse.class);
        when(container.readItem(id, new PartitionKey("mckesson"),
                CosmosDescriptionHandoffStorage.DescriptionHandoff.class)).thenReturn(response);
        when(response.getItem()).thenReturn(handoff);
        assertThat(storage.readDescription("mckesson", "JR1", id)).isEqualTo(source);
        assertThatThrownBy(() -> storage.readDescription("mckesson", "other", id))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> storage.readDescription("mckesson", "JR1", "raw/old-blob"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changedContentAndRunsHaveDifferentIds() {
        CosmosContainer container = mock(CosmosContainer.class);
        CosmosDescriptionHandoffStorage storage = new CosmosDescriptionHandoffStorage(container);
        String first = storage.writeDescription("mckesson", "run-1", "JR1", "first");
        assertThat(storage.writeDescription("mckesson", "run-1", "JR1", "changed")).isNotEqualTo(first);
        assertThat(storage.writeDescription("mckesson", "run-2", "JR1", "first")).isNotEqualTo(first);
        assertThat(storage.writeDescription("mckesson", "run-1", "JR1", "first")).isEqualTo(first);
    }
}
