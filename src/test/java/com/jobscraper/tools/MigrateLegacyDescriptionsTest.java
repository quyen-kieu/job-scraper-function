package com.jobscraper.tools;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosPatchItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrateLegacyDescriptionsTest {
    @Test
    void dryRunDoesNotPatchAndApplyUsesConcurrencyGuard() {
        CosmosContainer container = mock(CosmosContainer.class);
        @SuppressWarnings("unchecked")
        CosmosPagedIterable<MigrateLegacyDescriptions.LegacyPosting> documents = mock(CosmosPagedIterable.class);
        var legacy = new MigrateLegacyDescriptions.LegacyPosting(
                "mckesson:1", "mckesson", "Legacy text", null, "\"etag-value\"");
        when(container.queryItems(any(String.class), any(CosmosQueryRequestOptions.class),
                eq(MigrateLegacyDescriptions.LegacyPosting.class))).thenReturn(documents);
        when(documents.iterator()).thenAnswer(ignored -> List.of(legacy).iterator());

        assertThat(MigrateLegacyDescriptions.migrate(container, false))
                .isEqualTo(new MigrateLegacyDescriptions.Result(1, 0, 0));
        verify(container, never()).patchItem(any(), any(), any(), any(), eq(Object.class));

        assertThat(MigrateLegacyDescriptions.migrate(container, true))
                .isEqualTo(new MigrateLegacyDescriptions.Result(1, 1, 0));
        var options = org.mockito.ArgumentCaptor.forClass(CosmosPatchItemRequestOptions.class);
        verify(container).patchItem(eq("mckesson:1"), eq(new PartitionKey("mckesson")),
                any(CosmosPatchOperations.class), options.capture(), eq(Object.class));
        assertThat(options.getValue().getIfMatchETag()).isEqualTo("\"etag-value\"");
        assertThat(options.getValue().getFilterPredicate()).contains("_etag").contains("etag-value");
    }
}
