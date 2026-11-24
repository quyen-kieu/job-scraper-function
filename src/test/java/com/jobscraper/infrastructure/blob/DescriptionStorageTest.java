package com.jobscraper.infrastructure.blob;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DescriptionStorageTest {
    @Test
    void blobRoundTripsUtf8AndValidatesCompanyAndJob() throws Exception {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(container.getBlobClient(any())).thenReturn(blob);
        String text = "Caf\u00e9 \u2014 engineer";
        when(blob.downloadContent()).thenReturn(BinaryData.fromBytes(text.getBytes(StandardCharsets.UTF_8)));
        AzureBlobRawScrapeStorage storage = new AzureBlobRawScrapeStorage(container);

        String path = storage.writeDescription("mckesson", "run-1", "JR100", text);

        var input = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(blob).upload(input.capture(), eq((long) text.getBytes(StandardCharsets.UTF_8).length), eq(true));
        assertThat(new String(input.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(storage.readDescription("mckesson", "JR100", path)).isEqualTo(text);
        assertThatThrownBy(() -> storage.readDescription("first-citizens", "JR100", path))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.readDescription("mckesson", "other", path))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.readDescription("mckesson", "JR100", "raw/mckesson/../" + path))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noOpStorageExplicitlyRejectsDescriptions() {
        var storage = new NoOpRawScrapeStorage();
        assertThatThrownBy(() -> storage.writeDescription("mckesson", "run-1", "1", "text"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires");
        assertThatThrownBy(() -> storage.readDescription("mckesson", "1", "path"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires");
    }
}
