package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalizedPostingPersistenceHandlerTest {

    @Mock
    private JobPostingRepository postingRepository;

    @Mock
    private JobObservationRepository observationRepository;

    @Mock
    private RawScrapeStorage rawStorage;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldUpsertPostingAndObservation() throws Exception {
        NormalizedJobPosting posting = buildPosting();

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(posting));

        assertThat(result).isEqualTo(posting);
        verify(postingRepository).upsert(posting);
        verify(observationRepository).upsert(any(JobObservation.class));
    }

    @Test
    void shouldRoundTripUrlField() throws Exception {
        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(buildPosting()));

        assertThat(result.url()).isEqualTo("https://careers.mckesson.com/en/job/irving/lead/733/99215825472");
    }

    @Test
    void shouldAcceptLegacyMessagesWithoutUrl() throws Exception {
        String legacyJson = """
                {"id":"mckesson:1","companyId":"mckesson","externalJobId":"1","titleRaw":"Engineer",
                 "titleNormalized":"Engineer","jobLevel":"Mid-Level","location":"Remote",
                 "datePosted":"2026-08-14","firstSeenAt":"2026-08-15T10:00:00Z",
                 "lastSeenAt":"2026-08-15T10:00:00Z","isActive":true,"contentHash":"sha256:abc",
                 "schemaVersion":1}
                """;

        NormalizedJobPosting result = newHandler().handle(legacyJson);

        assertThat(result.url()).isNull();
        assertThat(result.description()).isNull();
        assertThat(result.descriptionPath()).isNull();
        verify(postingRepository).upsert(result);
    }

    @Test
    void shouldLoadDescriptionAndPersistItWithoutTheTransportPath() throws Exception {
        var wire = objectMapper.valueToTree(buildPosting());
        ((com.fasterxml.jackson.databind.node.ObjectNode) wire).put("descriptionPath", "raw/path");
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path"))
                .thenReturn("Build reliable systems \u2014 caf\u00e9");

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(wire));

        assertThat(result.description()).isEqualTo("Build reliable systems \u2014 caf\u00e9");
        assertThat(result.descriptionPath()).isNull();
        verify(postingRepository).upsert(result);
        verify(observationRepository).upsert(any(JobObservation.class));
    }

    @Test
    void shouldPreserveStoredDescriptionForLegacyOrFailedDetailEvents() throws Exception {
        NormalizedJobPosting existing = buildPosting().withDescription("Existing description");
        when(postingRepository.findById(existing.id(), existing.companyId())).thenReturn(Optional.of(existing));

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(buildPosting()));

        assertThat(result.description()).isEqualTo("Existing description");
        verify(postingRepository).upsert(result);
        verify(rawStorage, never()).readDescription(any(), any(), any());
    }

    @Test
    void shouldPropagateDescriptionReadFailureBeforeEitherUpsert() throws Exception {
        var wire = objectMapper.valueToTree(buildPosting());
        ((com.fasterxml.jackson.databind.node.ObjectNode) wire).put("descriptionPath", "raw/path");
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path"))
                .thenThrow(new IllegalStateException("Storage unavailable"));

        assertThatThrownBy(() -> newHandler().handle(objectMapper.writeValueAsString(wire)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Storage unavailable");
        verify(postingRepository, never()).upsert(any());
        verify(observationRepository, never()).upsert(any());
    }

    @Test
    void shouldRejectEmptyStoredDescriptionRatherThanOverwriteExistingText() throws Exception {
        var wire = objectMapper.valueToTree(buildPosting());
        ((com.fasterxml.jackson.databind.node.ObjectNode) wire).put("descriptionPath", "raw/path");
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path")).thenReturn(" ");

        assertThatThrownBy(() -> newHandler().handle(objectMapper.writeValueAsString(wire)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stored description is empty");
        verify(postingRepository, never()).upsert(any());
    }

    @Test
    void shouldPassStoredTextThroughNormalizationToThePostingUpsert(@TempDir Path directory) throws Exception {
        RawStorageProperties properties = new RawStorageProperties();
        properties.setFileRootPath(directory.toString());
        var storage = new LocalFileRawScrapeStorage(properties);
        String path = storage.writeDescription("mckesson", "run-1", "JR1", "Build caf\u00e9 systems");
        ScrapedJob job = new ScrapedJob("JR1", "Engineer", "Texas", null, "https://x/1", null, path);
        var event = new ScrapeCompletedEvent("run-1", "mckesson", fixedClock.instant(), fixedClock.instant(),
                1, 1, List.of(), List.of(), List.of(job));
        List<NormalizedJobPosting> published = new ArrayList<>();
        var normalizer = new ScrapeCompletedNormalizationHandler(
                objectMapper, published::add, postingRepository, ignored -> {});
        normalizer.handle(objectMapper.writeValueAsString(event));

        assertThat(published).singleElement().satisfies(posting -> {
            assertThat(posting.description()).isNull();
            assertThat(posting.descriptionPath()).isEqualTo(path);
        });
        var handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository, storage, fixedClock);
        NormalizedJobPosting saved = handler.handle(objectMapper.writeValueAsString(published.get(0)));

        assertThat(saved.description()).isEqualTo("Build caf\u00e9 systems");
        assertThat(saved.descriptionPath()).isNull();
        verify(postingRepository).upsert(saved);
    }

    @Test
    void shouldSerializeAndReadLegacyDocumentsWithCosmosSdkSerializer() {
        var serializer = com.azure.cosmos.CosmosItemSerializer.DEFAULT_SERIALIZER;
        var serialized = serializer.serialize(buildPosting().withDescription("Full description"));
        assertThat(serialized).containsEntry("description", "Full description");
        assertThat(serializer.deserialize(serialized, NormalizedJobPosting.class).description())
                .isEqualTo("Full description");
        var legacy = new java.util.LinkedHashMap<>(serialized);
        legacy.remove("description");
        legacy.remove("descriptionPath");
        assertThat(serializer.deserialize(legacy, NormalizedJobPosting.class).description()).isNull();
    }

    @Test
    void shouldDeriveDeterministicObservationIdFromScrapeDate() throws Exception {
        newHandler().handle(objectMapper.writeValueAsString(buildPosting()));

        ArgumentCaptor<JobObservation> captor = ArgumentCaptor.forClass(JobObservation.class);
        verify(observationRepository).upsert(captor.capture());

        JobObservation observation = captor.getValue();
        String expectedId = JobObservation.buildId("mckesson", LocalDate.of(2026, 8, 16), "99215825472");
        assertThat(observation.id()).isEqualTo(expectedId);
        assertThat(observation.companyId()).isEqualTo("mckesson");
        assertThat(observation.titleNormalized()).isEqualTo("Data Architect");
    }

    @Test
    void shouldPropagateRepositoryFailureSoOffsetIsNotCommitted() throws Exception {
        NormalizedJobPosting posting = buildPosting();
        doThrow(new IllegalStateException("Cosmos unavailable")).when(postingRepository).upsert(posting);

        assertThatThrownBy(() -> newHandler().handle(objectMapper.writeValueAsString(posting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cosmos unavailable");
    }

    private NormalizedPostingPersistenceHandler newHandler() {
        return new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository, rawStorage, fixedClock);
    }

    private NormalizedJobPosting buildPosting() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        return new NormalizedJobPosting(
                "mckesson:99215825472", "mckesson", "99215825472",
                "Lead Data Architect (Healthcare)", "Data Architect", "Lead",
                "Irving, TX", LocalDate.of(2026, 8, 14), now, now, true,
                "sha256:abc123", 1,
                "https://careers.mckesson.com/en/job/irving/lead/733/99215825472"
        );
    }
}
