package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.infrastructure.cosmos.CosmosDescriptionHandoffStorage;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;

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

    @BeforeEach
    void useDefaultConditionalWrite() {
        lenient().when(postingRepository.upsertIfCurrent(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenCallRealMethod();
    }

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

        assertThat(result.description()).isNull();
        assertThat(result.descriptionPlainText()).isEqualTo("Build reliable systems \u2014 caf\u00e9");
        assertThat(result.descriptionHtml()).isNull();
        assertThat(result.descriptionPath()).isNull();
        verify(postingRepository).upsert(result);
        verify(observationRepository).upsert(any(JobObservation.class));
    }

    @Test
    void shouldPersistOriginalHtmlAndReadablePlainText() throws Exception {
        var wire = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(buildPosting());
        wire.put("descriptionPath", "raw/path");
        wire.put("descriptionFormat", "HTML");
        String html = "<h2>Engineer &amp; Lead</h2><p>Build <b>reliable</b> systems.</p>";
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path")).thenReturn(html);

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(wire));

        assertThat(result.descriptionHtml()).isEqualTo(html);
        assertThat(result.descriptionPlainText()).isEqualTo("Engineer & Lead Build reliable systems.");
        assertThat(result.description()).isNull();
        assertThat(result.descriptionPath()).isNull();
        assertThat(result.descriptionFormat()).isNull();
        verify(postingRepository).upsert(result);
    }

    @Test
    void shouldUsePlainTextWhenSourceHasNoHtml() throws Exception {
        var wire = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(buildPosting());
        wire.put("descriptionPath", "raw/path");
        wire.put("descriptionFormat", "PLAIN_TEXT");
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path"))
                .thenReturn("Engineer &amp; Lead");

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(wire));

        assertThat(result.descriptionHtml()).isNull();
        assertThat(result.descriptionPlainText()).isEqualTo("Engineer & Lead");
        assertThat(result.description()).isNull();
    }

    @Test
    void shouldReplaceOldHtmlWhenNewSourceContainsOnlyPlainText() throws Exception {
        var wire = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(buildPosting());
        wire.put("descriptionPath", "raw/path");
        wire.put("descriptionFormat", "PLAIN_TEXT");
        when(rawStorage.readDescription("mckesson", "99215825472", "raw/path")).thenReturn("New text");

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(wire));

        assertThat(result.descriptionHtml()).isNull();
        assertThat(result.descriptionPlainText()).isEqualTo("New text");
        verify(postingRepository, never()).findById(any(), any());
    }

    @Test
    void shouldPreserveStoredDescriptionForLegacyOrFailedDetailEvents() throws Exception {
        NormalizedJobPosting existing = buildPosting()
                .withDescriptions("<p>Existing description</p>", "Existing description");
        when(postingRepository.findById(existing.id(), existing.companyId())).thenReturn(Optional.of(existing));

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(buildPosting()));

        assertThat(result.description()).isNull();
        assertThat(result.descriptionPlainText()).isEqualTo("Existing description");
        assertThat(result.descriptionHtml()).isEqualTo("<p>Existing description</p>");
        verify(postingRepository).upsert(result);
        verify(rawStorage, never()).readDescription(any(), any(), any());
    }

    @Test
    void shouldMigrateLegacyDescriptionWhenLaterFetchFails() throws Exception {
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(buildPosting());
        legacy.put("description", "Legacy text");
        legacy.remove(List.of("descriptionPlainText", "descriptionHtml", "descriptionFormat"));
        NormalizedJobPosting existing = objectMapper.treeToValue(legacy, NormalizedJobPosting.class);
        when(postingRepository.findById(existing.id(), existing.companyId())).thenReturn(Optional.of(existing));

        NormalizedJobPosting result = newHandler().handle(objectMapper.writeValueAsString(buildPosting()));

        assertThat(result.description()).isNull();
        assertThat(result.descriptionPlainText()).isEqualTo("Legacy text");
        assertThat(result.descriptionHtml()).isNull();
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
        String path = storage.writeDescription("mckesson", "run-1", "JR1", "<p>Build <b>caf\u00e9</b> systems</p>");
        ScrapedJob job = new ScrapedJob("JR1", "Engineer", "Texas", null, "https://x/1",
                null, path, DescriptionFormat.HTML);
        var event = new ScrapeCompletedEvent("run-1", "mckesson", fixedClock.instant(), fixedClock.instant(),
                1, 1, List.of(), List.of(), List.of(job));
        List<NormalizedJobPosting> published = new ArrayList<>();
        var normalizer = new ScrapeCompletedNormalizationHandler(
                objectMapper, published::add, postingRepository, ignored -> {});
        normalizer.handle(objectMapper.writeValueAsString(event));

        assertThat(published).singleElement().satisfies(posting -> {
            assertThat(posting.description()).isNull();
            assertThat(posting.descriptionHtml()).isNull();
            assertThat(posting.descriptionPlainText()).isNull();
            assertThat(posting.descriptionPath()).isEqualTo(path);
            assertThat(posting.descriptionFormat()).isEqualTo(DescriptionFormat.HTML);
        });
        var handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository, storage, fixedClock);
        NormalizedJobPosting saved = handler.handle(objectMapper.writeValueAsString(published.get(0)));

        assertThat(saved.description()).isNull();
        assertThat(saved.descriptionPlainText()).isEqualTo("Build caf\u00e9 systems");
        assertThat(saved.descriptionHtml()).isEqualTo("<p>Build <b>caf\u00e9</b> systems</p>");
        assertThat(saved.descriptionPath()).isNull();
        verify(postingRepository).upsert(saved);
    }

    @Test
    void shouldPassCosmosHandoffThroughNormalizationAndPersistBothDescriptions() throws Exception {
        var container = org.mockito.Mockito.mock(com.azure.cosmos.CosmosContainer.class);
        var storage = new CosmosDescriptionHandoffStorage(container);
        String html = "<p>Build <b>caf\u00e9</b> systems</p>";
        String reference = storage.writeDescription("mckesson", "run-1", "JR1", html);
        var handoff = new CosmosDescriptionHandoffStorage.DescriptionHandoff(
                reference, "mckesson", "run-1", "JR1", html);
        @SuppressWarnings("unchecked")
        com.azure.cosmos.models.CosmosItemResponse<CosmosDescriptionHandoffStorage.DescriptionHandoff> response =
                org.mockito.Mockito.mock(com.azure.cosmos.models.CosmosItemResponse.class);
        when(container.readItem(reference, new com.azure.cosmos.models.PartitionKey("mckesson"),
                CosmosDescriptionHandoffStorage.DescriptionHandoff.class)).thenReturn(response);
        when(response.getItem()).thenReturn(handoff);

        var job = new ScrapedJob("JR1", "Engineer", "Texas", null, "https://x/1",
                null, reference, DescriptionFormat.HTML);
        var event = new ScrapeCompletedEvent("run-1", "mckesson", fixedClock.instant(), fixedClock.instant(),
                1, 1, List.of(), List.of(), List.of(job));
        String eventJson = objectMapper.writeValueAsString(event);
        assertThat(eventJson).doesNotContain(html).contains(reference);
        List<NormalizedJobPosting> published = new ArrayList<>();
        new ScrapeCompletedNormalizationHandler(objectMapper, published::add, postingRepository, ignored -> {})
                .handle(eventJson);
        assertThat(published).hasSize(1);
        String postingJson = objectMapper.writeValueAsString(published.get(0));
        assertThat(postingJson).doesNotContain(html).contains(reference);

        var handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository, storage, fixedClock);
        NormalizedJobPosting saved = handler.handle(postingJson);
        assertThat(saved.descriptionHtml()).isEqualTo(html);
        assertThat(saved.descriptionPlainText()).isEqualTo("Build caf\u00e9 systems");
        assertThat(saved.descriptionPath()).isNull();
        assertThat(objectMapper.writeValueAsString(saved)).doesNotContain("\"description\":");
        verify(postingRepository).upsert(saved);
    }

    @Test
    void shouldSerializeAndReadLegacyDocumentsWithCosmosSdkSerializer() {
        var serializer = com.azure.cosmos.CosmosItemSerializer.DEFAULT_SERIALIZER;
        var serialized = serializer.serialize(
                buildPosting().withDescriptions("<p>Full description</p>", "Full description"));
        assertThat(serialized).doesNotContainKey("description");
        assertThat(serialized).doesNotContainKeys("descriptionPath", "descriptionFormat");
        assertThat(serialized).containsEntry("descriptionPlainText", "Full description");
        assertThat(serialized).containsEntry("descriptionHtml", "<p>Full description</p>");
        assertThat(serialized).containsEntry("isActive", true);
        assertThat(serializer.deserialize(serialized, NormalizedJobPosting.class).description()).isNull();
        assertThat(serializer.deserialize(serialized, NormalizedJobPosting.class).descriptionHtml())
                .isEqualTo("<p>Full description</p>");
        var legacy = new java.util.LinkedHashMap<>(serialized);
        legacy.put("description", "Legacy text");
        legacy.remove("descriptionPath");
        legacy.remove("descriptionHtml");
        legacy.remove("descriptionPlainText");
        legacy.remove("descriptionFormat");
        assertThat(serializer.deserialize(legacy, NormalizedJobPosting.class).description()).isEqualTo("Legacy text");
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
    void hybridObservationUsesOriginalRunAndScrapeDate() throws Exception {
        newHandler().persist(buildPosting().withRun("origin-run", false));
        ArgumentCaptor<JobObservation> captor = ArgumentCaptor.forClass(JobObservation.class);
        verify(observationRepository).upsert(captor.capture());
        assertThat(captor.getValue().runId()).isEqualTo("origin-run");
        assertThat(captor.getValue().scrapeDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void shouldPropagateRepositoryFailureSoOffsetIsNotCommitted() throws Exception {
        NormalizedJobPosting posting = buildPosting();
        doThrow(new IllegalStateException("Cosmos unavailable")).when(postingRepository).upsert(posting);

        assertThatThrownBy(() -> newHandler().handle(objectMapper.writeValueAsString(posting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cosmos unavailable");
    }

    @Test
    void shouldNotPersistEventsAlreadyWrittenByNormalizer() throws Exception {
        NormalizedJobPosting posting = buildPosting().withRun("run", true);
        assertThat(newHandler().handle(objectMapper.writeValueAsString(posting))).isEqualTo(posting);
        verify(postingRepository, never()).upsertIfCurrent(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(observationRepository, never()).upsert(any());
    }

    @Test
    void staleEventDoesNotWriteAnObservation() throws Exception {
        org.mockito.Mockito.doReturn(false).when(postingRepository)
                .upsertIfCurrent(any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(newHandler().persist(buildPosting())).isEmpty();
        verify(observationRepository, never()).upsert(any());
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
