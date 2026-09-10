package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.notification.RunCompletionAggregator;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalizedPostingPersistenceHandlerTest {

    @Mock
    private JobPostingRepository postingRepository;

    @Mock
    private JobObservationRepository observationRepository;

    @Mock
    private RunCompletionAggregator runCompletionAggregator;

    @Mock
    private DailyNotificationPublisher dailyNotificationPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(postingRepository.findById(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        lenient().when(runCompletionAggregator.recordPersistedPosting(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldUpsertPostingAndObservation() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        String json = objectMapper.writeValueAsString(posting);

        NormalizedJobPosting result = handler.handle(json);

        assertThat(result).isEqualTo(posting);
        verify(postingRepository).upsert(posting);
        verify(observationRepository).upsert(org.mockito.ArgumentMatchers.any(JobObservation.class));
    }

    @Test
    void shouldClassifyOutcomeAsNewWhenNoPriorDocumentExists() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        when(postingRepository.findById(posting.id(), posting.companyId())).thenReturn(Optional.empty());

        handler.handle(objectMapper.writeValueAsString(posting));

        verify(runCompletionAggregator).recordPersistedPosting("mckesson", PersistenceOutcome.NEW);
    }

    @Test
    void shouldClassifyOutcomeAsUpdatedWhenContentHashDiffers() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        NormalizedJobPosting priorVersion = new NormalizedJobPosting(
                posting.id(), posting.companyId(), posting.externalJobId(), posting.titleRaw(),
                posting.titleNormalized(), posting.jobLevel(), posting.location(), posting.datePosted(),
                posting.firstSeenAt(), posting.firstSeenAt(), true, "sha256:different", 1);
        when(postingRepository.findById(posting.id(), posting.companyId())).thenReturn(Optional.of(priorVersion));

        handler.handle(objectMapper.writeValueAsString(posting));

        verify(runCompletionAggregator).recordPersistedPosting("mckesson", PersistenceOutcome.UPDATED);
    }

    @Test
    void shouldClassifyOutcomeAsUnchangedWhenContentHashMatches() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        when(postingRepository.findById(posting.id(), posting.companyId())).thenReturn(Optional.of(posting));

        handler.handle(objectMapper.writeValueAsString(posting));

        verify(runCompletionAggregator).recordPersistedPosting("mckesson", PersistenceOutcome.UNCHANGED);
    }

    @Test
    void shouldPublishDailyNotificationWhenAggregatorReportsCompanyRunComplete() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        DailyNotificationRequestedEvent completedEvent = new DailyNotificationRequestedEvent(
                "run-123", LocalDate.of(2026, 8, 16), 1, 1, 0, 0, java.util.List.of());
        when(runCompletionAggregator.recordPersistedPosting("mckesson", PersistenceOutcome.NEW))
                .thenReturn(Optional.of(completedEvent));

        handler.handle(objectMapper.writeValueAsString(posting));

        verify(dailyNotificationPublisher).publish(completedEvent);
    }

    @Test
    void shouldDeriveDeterministicObservationIdFromScrapeDate() throws Exception {
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        String json = objectMapper.writeValueAsString(posting);

        handler.handle(json);

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
        NormalizedPostingPersistenceHandler handler = new NormalizedPostingPersistenceHandler(
                objectMapper, postingRepository, observationRepository,
                runCompletionAggregator, dailyNotificationPublisher, fixedClock);

        NormalizedJobPosting posting = buildPosting();
        String json = objectMapper.writeValueAsString(posting);

        doThrow(new IllegalStateException("Cosmos unavailable")).when(postingRepository).upsert(posting);

        assertThatThrownBy(() -> handler.handle(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cosmos unavailable");
    }

    private NormalizedJobPosting buildPosting() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        return new NormalizedJobPosting(
                "mckesson:99215825472", "mckesson", "99215825472",
                "Lead Data Architect (Healthcare)", "Data Architect", "Lead",
                "Irving, TX", LocalDate.of(2026, 8, 14), now, now, true,
                "sha256:abc123", 1
        );
    }
}

