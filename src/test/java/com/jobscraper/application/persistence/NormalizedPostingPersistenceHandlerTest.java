package com.jobscraper.application.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.domain.job.JobObservation;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NormalizedPostingPersistenceHandlerTest {

    @Mock
    private JobPostingRepository postingRepository;

    @Mock
    private JobObservationRepository observationRepository;

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
        verify(postingRepository).upsert(result);
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
                objectMapper, postingRepository, observationRepository, fixedClock);
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

