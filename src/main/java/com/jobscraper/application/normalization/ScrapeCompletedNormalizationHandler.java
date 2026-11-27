package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.notification.NotificationPosting;
import com.jobscraper.application.persistence.PersistenceOutcome;
import com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler;
import com.jobscraper.domain.job.ContentHasher;
import com.jobscraper.domain.job.JobLevelClassifier;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.domain.job.TitleNormalizer;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.scraper.api.ScrapedJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application service that deserializes a {@code job.scrape.completed} message, derives
 * normalized job postings, persists them, publishes them to {@code job.postings.normalized},
 * and publishes one {@link DailyNotificationRequestedEvent} to {@code notifications.daily}.
 *
 * <p><b>Posting source:</b> postings are built from the structured {@link ScrapedJob} list
 * carried on the event (real title, location, date posted, URL). For messages produced before
 * that field existed, it falls back to deriving IDs from {@code job-{externalJobId}.html} blob
 * paths with placeholder titles.</p>
 *
 * <p>The per-company run list enables reconciliation only after all of its postings and
 * observations have been persisted. Kafka publication alone is not a persistence barrier.</p>
 *
 * <p><b>Path handling:</b> blob paths may use either {@code /}-separated Azure Blob keys or
 * OS-native absolute filesystem paths; only the final path segment is matched.</p>
 */
@Service
public class ScrapeCompletedNormalizationHandler {
    private static final Logger LOGGER = Logger.getLogger(ScrapeCompletedNormalizationHandler.class.getName());

    private static final Pattern JOB_DETAIL_FILE_NAME_PATTERN = Pattern.compile("^job-(.+)\\.html$");
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final NormalizedPostingPublisher publisher;
    private final JobPostingRepository postingRepository;
    private final DailyNotificationPublisher dailyNotificationPublisher;
    private final NormalizedPostingPersistenceHandler persistenceHandler;
    private final Clock clock;

    @Autowired
    public ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            JobPostingRepository postingRepository,
            DailyNotificationPublisher dailyNotificationPublisher,
            NormalizedPostingPersistenceHandler persistenceHandler) {
        this(objectMapper, publisher, postingRepository, dailyNotificationPublisher, persistenceHandler, Clock.systemUTC());
    }

    public ScrapeCompletedNormalizationHandler(ObjectMapper objectMapper, NormalizedPostingPublisher publisher,
                                                JobPostingRepository postingRepository,
                                                DailyNotificationPublisher dailyNotificationPublisher) {
        this(objectMapper, publisher, postingRepository, dailyNotificationPublisher, null, Clock.systemUTC());
    }

    ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            JobPostingRepository postingRepository,
            DailyNotificationPublisher dailyNotificationPublisher,
            Clock clock) {
        this(objectMapper, publisher, postingRepository, dailyNotificationPublisher, null, clock);
    }

    ScrapeCompletedNormalizationHandler(ObjectMapper objectMapper, NormalizedPostingPublisher publisher,
                                        JobPostingRepository postingRepository,
                                        DailyNotificationPublisher dailyNotificationPublisher,
                                        NormalizedPostingPersistenceHandler persistenceHandler, Clock clock) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.postingRepository = postingRepository;
        this.dailyNotificationPublisher = dailyNotificationPublisher;
        this.persistenceHandler = persistenceHandler;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.scrape.completed} event.
     *
     * <ol>
     *   <li>Deserialize JSON into {@link ScrapeCompletedEvent}</li>
     *   <li>Build one {@link NormalizedJobPosting} per scraped job</li>
     *   <li>Classify, persist, and publish each posting</li>
     *   <li>Reconcile a complete successful full refresh, then publish the notification</li>
     * </ol>
     *
     * @param json the raw JSON message
     * @return the number of postings published
     * @throws Exception any deserialization, lookup, or publishing failure (triggers redelivery)
     */
    public int handle(String json) throws Exception {
        ScrapeCompletedEvent event = objectMapper.readValue(json, ScrapeCompletedEvent.class);
        Instant now = Instant.now(clock);

        List<NotificationPosting> notificationPostings = new ArrayList<>();
        int newCount = 0;
        int changedCount = 0;
        List<ScrapedJob> jobs = resolveJobs(event);

        for (ScrapedJob job : jobs) {
            String id = NormalizedJobPosting.buildId(event.companyId(), job.externalJobId());
            Optional<NormalizedJobPosting> existing = postingRepository.findById(id, event.companyId());

            Instant observedAt = persistenceHandler == null ? now : event.startedAt();
            NormalizedJobPosting posting = toNormalizedPosting(event.companyId(), job, existing, observedAt)
                    .withRun(event.runId(), false)
                    .withSalaryRange(job.descriptionPath() != null || job.salaryRange() != null
                            ? job.salaryRange()
                            : existing.map(NormalizedJobPosting::salaryRange).orElse(null));
            PersistenceOutcome outcome = classify(posting, existing);

            if (persistenceHandler != null) {
                if (persistenceHandler.persist(posting).isEmpty()) {
                    continue;
                }
                posting = posting.withRun(event.runId(), true);
            }
            publisher.publish(posting);

            if (outcome == PersistenceOutcome.NEW) {
                newCount++;
            } else if (outcome == PersistenceOutcome.UPDATED) {
                changedCount++;
            }
            notificationPostings.add(new NotificationPosting(
                    job.externalJobId(), job.title(), job.location(), posting.datePosted(), job.url(),
                    posting.salaryRange(), outcome));
        }

        int inactiveCount = 0;
        List<String> failures = new ArrayList<>(event.errors());
        if (event.fullDetailRefresh() != null && !event.searchComplete()) {
            failures.add("Search enumeration was incomplete or inconsistent; reconciliation was skipped.");
        }
        if (persistenceHandler != null && Boolean.TRUE.equals(event.fullDetailRefresh())
                && event.searchComplete() && event.errors().isEmpty()) {
            Set<String> seenIds = jobs.stream().map(ScrapedJob::externalJobId).collect(Collectors.toSet());
            var reconciliation = postingRepository.reconcile(
                    event.companyId(), event.runId(), event.startedAt(), seenIds);
            if (reconciliation.completed()) {
                inactiveCount = reconciliation.inactiveCount();
                LOGGER.info("Reconciliation completed companyId=" + event.companyId() + " runId=" + event.runId()
                        + " inactiveCount=" + inactiveCount);
            } else {
                LOGGER.warning("Reconciliation skipped companyId=" + event.companyId() + " runId=" + event.runId()
                        + " reason=empty results, disabled persistence, or a newer run");
                failures.add("Reconciliation skipped: empty results, disabled persistence, or a newer run.");
            }
        }

        dailyNotificationPublisher.publish(new DailyNotificationRequestedEvent(
                event.runId(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)),
                1,
                newCount,
                changedCount,
                inactiveCount,
                failures,
                event.companyId(),
                notificationPostings
        ));

        return notificationPostings.size();
    }

    /**
     * Returns the structured job list, or (for legacy messages without it) jobs derived from
     * detail-page blob paths with placeholder titles. De-duplicated by external job ID.
     */
    private static List<ScrapedJob> resolveJobs(ScrapeCompletedEvent event) {
        Map<String, ScrapedJob> byId = new LinkedHashMap<>();
        if (event.fullDetailRefresh() != null || !event.jobs().isEmpty()) {
            for (ScrapedJob job : event.jobs()) {
                byId.putIfAbsent(job.externalJobId(), job);
            }
            return List.copyOf(byId.values());
        }

        for (String blobPath : event.blobPaths()) {
            String externalJobId = extractExternalJobId(blobPath);
            if (externalJobId != null) {
                byId.putIfAbsent(externalJobId, new ScrapedJob(
                        externalJobId,
                        "Unknown title (pending detail-page fetch): " + externalJobId,
                        "Unknown",
                        null,
                        null,
                        null));
            }
        }
        return List.copyOf(byId.values());
    }

    private static PersistenceOutcome classify(
            NormalizedJobPosting posting, Optional<NormalizedJobPosting> existing) {
        return existing
                .map(prior -> prior.contentHash().equals(posting.contentHash())
                        ? PersistenceOutcome.UNCHANGED
                        : PersistenceOutcome.UPDATED)
                .orElse(PersistenceOutcome.NEW);
    }

    /**
     * Extracts the external job ID from a blob path's final file-name segment, tolerating both
     * {@code /}-separated blob keys and OS-native absolute paths (including Windows backslash
     * paths). Returns {@code null} when the file name does not match the
     * {@code job-{externalJobId}.html} convention (e.g. {@code search-page-1.json}).
     */
    private static String extractExternalJobId(String blobPath) {
        int lastSeparator = Math.max(blobPath.lastIndexOf('/'), blobPath.lastIndexOf('\\'));
        String fileName = lastSeparator >= 0 ? blobPath.substring(lastSeparator + 1) : blobPath;

        Matcher matcher = JOB_DETAIL_FILE_NAME_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private NormalizedJobPosting toNormalizedPosting(
            String companyId, ScrapedJob job, Optional<NormalizedJobPosting> existing, Instant now) {
        LocalDate datePosted = job.descriptionPath() == null
                ? existing.map(NormalizedJobPosting::datePosted).orElse(job.datePosted())
                : job.datePosted();
        if (datePosted == null) {
            datePosted = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        }
        Instant firstSeenAt = existing.map(NormalizedJobPosting::firstSeenAt).orElse(now);

        return new NormalizedJobPosting(
                NormalizedJobPosting.buildId(companyId, job.externalJobId()),
                companyId,
                job.externalJobId(),
                job.title(),
                TitleNormalizer.normalize(job.title()),
                JobLevelClassifier.classify(job.title()),
                job.location(),
                datePosted,
                firstSeenAt,
                now,
                true,
                job.descriptionPath() == null && existing.isPresent()
                        && existing.get().titleRaw().equals(job.title())
                        && existing.get().location().equals(job.location())
                        ? existing.get().contentHash()
                        : ContentHasher.sha256(job.title() + "|" + job.location() + "|" + job.datePosted()),
                SCHEMA_VERSION,
                job.url(),
                null,
                job.descriptionPath(),
                job.descriptionFormat(),
                null,
                null
        );
    }
}
