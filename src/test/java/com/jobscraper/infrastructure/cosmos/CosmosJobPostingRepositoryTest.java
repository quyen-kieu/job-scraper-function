package com.jobscraper.infrastructure.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.application.command.HybridScrapePolicy;
import com.jobscraper.application.command.ScrapeCompanyCommand;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.ScrapedJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmosJobPostingRepositoryTest {
    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private final CosmosContainer container = mock(CosmosContainer.class);
    private final Map<String, Object> documents = new LinkedHashMap<>();
    private CosmosJobPostingRepository repository;
    private Runnable conflictAction;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        repository = new CosmosJobPostingRepository(container);
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Object item = documents.get(id);
            if (item == null) {
                CosmosException notFound = mock(CosmosException.class);
                when(notFound.getStatusCode()).thenReturn(404);
                throw notFound;
            }
            return response(item);
        }).when(container).readItem(anyString(), any(PartitionKey.class), any(Class.class));
        doAnswer(invocation -> {
            Object item = invocation.getArgument(0);
            documents.put(CompanyScrapeState.ID, item);
            return response(item);
        }).when(container).createItem(any(), any(PartitionKey.class), any(CosmosItemRequestOptions.class));
        doAnswer(invocation -> {
            Object item = invocation.getArgument(0);
            documents.put(invocation.getArgument(1), item);
            return response(item);
        }).when(container).replaceItem(any(), anyString(), any(PartitionKey.class), any(CosmosItemRequestOptions.class));
        doAnswer(invocation -> {
            CosmosBatch batch = invocation.getArgument(0);
            assertThat(batch.getPartitionKeyValue()).isEqualTo(new PartitionKey("company"));
            if (conflictAction != null) {
                Runnable action = conflictAction;
                conflictAction = null;
                action.run();
                CosmosBatchResponse result = mock(CosmosBatchResponse.class);
                when(result.isSuccessStatusCode()).thenReturn(false);
                when(result.getStatusCode()).thenReturn(412);
                return result;
            }
            batch.getOperations().forEach(operation -> {
                if (operation.getOperationType() == com.azure.cosmos.models.CosmosItemOperationType.REPLACE) {
                    var internal = (com.azure.cosmos.implementation.batch.ItemBatchOperation<?>) operation;
                    assertThat(internal.getRequestOptions().getIfMatchETag()).isEqualTo("etag");
                }
                Object item = operation.getItem();
                String id = item instanceof CompanyScrapeState state ? state.id()
                        : ((NormalizedJobPosting) item).id();
                documents.put(id, item);
            });
            CosmosBatchResponse result = mock(CosmosBatchResponse.class);
            when(result.isSuccessStatusCode()).thenReturn(true);
            return result;
        }).when(container).executeCosmosBatch(any(CosmosBatch.class));
        CosmosPagedIterable<NormalizedJobPosting> query = mock(CosmosPagedIterable.class);
        when(query.iterator()).thenAnswer(invocation -> documents.values().stream()
                .filter(NormalizedJobPosting.class::isInstance)
                .map(NormalizedJobPosting.class::cast).filter(NormalizedJobPosting::isActive)
                .toList().iterator());
        doAnswer(invocation -> {
            java.util.function.Consumer<NormalizedJobPosting> consumer = invocation.getArgument(0);
            query.iterator().forEachRemaining(consumer);
            return null;
        }).when(query).forEach(any());
        when(container.queryItems(anyString(), any(CosmosQueryRequestOptions.class), eqPostingClass()))
                .thenReturn(query);
    }

    @Test
    void reconcilesWithoutDeletingAndRejectsLateOlderEvents() {
        repository.upsertIfCurrent(posting("1", T1, "old"), false);
        repository.upsertIfCurrent(posting("2", T1, "old"), false);
        Instant next = T1.plusSeconds(60);
        repository.upsertIfCurrent(posting("1", next, "new"), false);

        ReconciliationResult result = repository.reconcile("company", "new", next, Set.of("1"));

        assertThat(result).isEqualTo(new ReconciliationResult(true, 1));
        assertThat(repository.findById("company:2", "company")).get()
                .extracting(NormalizedJobPosting::isActive).isEqualTo(false);
        assertThat(repository.upsertIfCurrent(posting("2", T1, "old"), false)).isFalse();
        assertThat(repository.findById("company:2", "company")).get()
                .extracting(NormalizedJobPosting::isActive).isEqualTo(false);
        assertThat(repository.upsertIfCurrent(posting("3", T1, "old"), false)).isFalse();
        assertThat(repository.lastSuccessfulReconciliationAt("company")).contains(next);
    }

    @Test
    void supportsRetryAndReactivationAndPreservesDescriptions() {
        repository.upsertIfCurrent(posting("1", T1, "old").withDescription("Original"), false);
        repository.upsertIfCurrent(posting("2", T1, "old"), false);
        Instant next = T1.plusSeconds(60);
        repository.upsertIfCurrent(posting("2", next, "new"), false);
        assertThat(repository.reconcile("company", "new", next, Set.of("2")).inactiveCount()).isEqualTo(1);
        assertThat(repository.reconcile("company", "new", next, Set.of("2")).inactiveCount()).isEqualTo(1);

        repository.upsertIfCurrent(posting("1", next.plusSeconds(1), "later"), true);
        assertThat(repository.findById("company:1", "company")).get().satisfies(posting -> {
            assertThat(posting.isActive()).isTrue();
            assertThat(posting.descriptionPlainText()).isEqualTo("Original");
            assertThat(posting.firstSeenAt()).isEqualTo(T1);
        });
    }

    @Test
    void emptySearchDoesNotAdvanceWatermarkOrInactivate() {
        repository.upsertIfCurrent(posting("1", T1, "old"), false);
        assertThat(repository.reconcile("company", "new", T1.plusSeconds(60), Set.of()).completed()).isFalse();
        assertThat(repository.findById("company:1", "company")).get()
                .extracting(NormalizedJobPosting::isActive).isEqualTo(true);
        assertThat(repository.lastSuccessfulReconciliationAt("company")).isEmpty();
    }

    @Test
    void controlDocumentRoundTripsThroughCosmosSerializer() {
        var serializer = com.azure.cosmos.CosmosItemSerializer.DEFAULT_SERIALIZER;
        CompanyScrapeState state = CompanyScrapeState.empty("company").begin(T1, "run").complete();
        var document = serializer.serialize(state);
        assertThat(document).containsEntry("recordType", "scrape-control");
        assertThat(document).doesNotContainKey("externalJobId");
        assertThat(serializer.deserialize(document, CompanyScrapeState.class)).isEqualTo(state);
    }

    @Test
    void retriesConflictAndRejectsEventWhenWatermarkAdvanced() {
        repository.upsertIfCurrent(posting("1", T1, "old"), false);
        Instant newer = T1.plusSeconds(60);
        conflictAction = () -> documents.put(CompanyScrapeState.ID,
                CompanyScrapeState.empty("company").begin(newer, "new"));

        assertThat(repository.upsertIfCurrent(posting("1", T1.plusSeconds(1), "late"), false)).isFalse();
        assertThat(repository.findById("company:1", "company")).get()
                .extracting(NormalizedJobPosting::lastSeenAt).isEqualTo(T1);
    }

    @Test
    void retryDoesNotInactivateAConcurrentlyReappearingPosting() {
        repository.upsertIfCurrent(posting("1", T1, "old"), false);
        repository.upsertIfCurrent(posting("2", T1, "old"), false);
        Instant scanTime = T1.plusSeconds(60);
        conflictAction = () -> documents.put("company:1", posting("1", scanTime.plusSeconds(1), "later"));

        ReconciliationResult result = repository.reconcile("company", "scan", scanTime, Set.of("2"));

        assertThat(result).isEqualTo(new ReconciliationResult(true, 0));
        assertThat(repository.findById("company:1", "company")).get()
                .extracting(NormalizedJobPosting::isActive).isEqualTo(true);
    }

    @Test
    void completesBackfillDailyIncrementAndWeeklyReconciliation() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RawScrapeStorage storage = mock(RawScrapeStorage.class);
        when(storage.readDescription(anyString(), anyString(), anyString())).thenReturn("Job description");
        JobObservationRepository observations = mock(JobObservationRepository.class);
        var persistence = new NormalizedPostingPersistenceHandler(mapper, repository, observations, storage);
        List<DailyNotificationRequestedEvent> notifications = new ArrayList<>();
        var normalizer = new ScrapeCompletedNormalizationHandler(mapper, ignored -> {}, repository,
                notifications::add, persistence);
        HybridScrapePolicy policy = new HybridScrapePolicy(repository);

        assertThat(policy.createContext(new ScrapeCompanyCommand("initial", "company", T1), T1)
                .fullDetailRefresh()).isTrue();
        run(normalizer, mapper, "initial", T1, true,
                List.of(job("1", "Engineer", "details1"), job("2", "Engineer", "details2")));
        assertThat(repository.lastSuccessfulReconciliationAt("company")).contains(T1);

        Instant daily = T1.plusSeconds(86400);
        var context = policy.createContext(new ScrapeCompanyCommand("daily", "company", daily), daily);
        assertThat(context.fullDetailRefresh()).isFalse();
        assertThat(context.shouldFetchDetail(job("1", "Engineer", null), true)).isFalse();
        assertThat(context.shouldFetchDetail(job("2", "Senior Engineer", null), true)).isTrue();
        assertThat(context.shouldFetchDetail(job("3", "Engineer", null), true)).isTrue();
        run(normalizer, mapper, "daily", daily, false, List.of(job("1", "Engineer", null),
                job("2", "Senior Engineer", "details2-new"), job("3", "Engineer", "details3")));
        assertThat(repository.findById("company:1", "company")).get()
                .extracting(NormalizedJobPosting::descriptionPlainText).isEqualTo("Job description");

        Instant weekly = T1.plusSeconds(7 * 86400);
        assertThat(policy.createContext(new ScrapeCompanyCommand("weekly", "company", weekly), weekly)
                .fullDetailRefresh()).isTrue();
        run(normalizer, mapper, "weekly", weekly, true,
                List.of(job("2", "Senior Engineer", "details2-weekly"), job("3", "Engineer", "details3-weekly")));
        assertThat(repository.findById("company:1", "company")).get()
                .extracting(NormalizedJobPosting::isActive).isEqualTo(false);
        assertThat(notifications.get(2).inactiveCount()).isEqualTo(1);
        org.mockito.Mockito.verify(observations, org.mockito.Mockito.times(7)).upsert(any());
    }

    private static void run(ScrapeCompletedNormalizationHandler normalizer, ObjectMapper mapper,
                            String runId, Instant start, boolean full, List<ScrapedJob> jobs) throws Exception {
        ScrapeCompletedEvent event = new ScrapeCompletedEvent(runId, "company", start, start.plusSeconds(1),
                1, jobs.size(), List.of(), List.of(), jobs, true, full);
        normalizer.handle(mapper.writeValueAsString(event));
    }

    private static ScrapedJob job(String id, String title, String path) {
        return new ScrapedJob(id, title, "TX", LocalDate.of(2026, 1, 1), "https://example.test/" + id,
                null, path);
    }

    @SuppressWarnings("unchecked")
    private static <T> CosmosItemResponse<T> response(T item) {
        CosmosItemResponse<T> result = mock(CosmosItemResponse.class);
        when(result.getItem()).thenReturn(item);
        when(result.getETag()).thenReturn("etag");
        return result;
    }

    private static Class<NormalizedJobPosting> eqPostingClass() {
        return org.mockito.ArgumentMatchers.eq(NormalizedJobPosting.class);
    }

    private static NormalizedJobPosting posting(String id, Instant time, String runId) {
        return new NormalizedJobPosting("company:" + id, "company", id, "Engineer", "Engineer",
                "Mid-Level", "TX", LocalDate.of(2026, 1, 1), time, time, true, "hash", 1)
                .withRun(runId, false);
    }
}
