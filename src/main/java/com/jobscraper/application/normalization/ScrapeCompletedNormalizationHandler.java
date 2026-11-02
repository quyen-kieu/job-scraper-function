package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.jobscraper.application.notification.NotificationPosting;
import com.jobscraper.application.persistence.PersistenceOutcome;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application service that deserializes a {@code job.scrape.completed} message, derives
 * normalized job postings, publishes each to {@code job.postings.normalized}, and publishes one
 * {@link DailyNotificationRequestedEvent} (listing every scraped posting) to
 * {@code notifications.daily}.
 *
 * <p><b>Posting source:</b> postings are built from the structured {@link ScrapedJob} list
 * carried on the event (real title, location, date posted, URL). For messages produced before
 * that field existed, it falls back to deriving IDs from {@code job-{externalJobId}.html} blob
 * paths with placeholder titles.</p>
 *
 * <p><b>Why the notification is built here:</b> this is the only step that sees a company run's
 * complete posting list in a single invocation. Each posting is classified as
 * {@link PersistenceOutcome#NEW}/{@link PersistenceOutcome#UPDATED}/{@link PersistenceOutcome#UNCHANGED}
 * by reading the currently stored document (before the persistence consumer overwrites it).
 * This replaces the earlier in-memory run-completion aggregator, which could not work on Flex
 * Consumption: the normalizer and persistence functions scale on separate instances and never
 * shared heap state, so no notification was ever emitted.</p>
 *
 * <p><b>Path handling:</b> blob paths may use either {@code /}-separated Azure Blob keys or
 * OS-native absolute filesystem paths; only the final path segment is matched.</p>
 */
@Service
public class ScrapeCompletedNormalizationHandler {

    private static final Pattern JOB_DETAIL_FILE_NAME_PATTERN = Pattern.compile("^job-(.+)\\.html$");
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final NormalizedPostingPublisher publisher;
    private final JobPostingRepository postingRepository;
    private final DailyNotificationPublisher dailyNotificationPublisher;
    private final Clock clock;

    @Autowired
    public ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            JobPostingRepository postingRepository,
            DailyNotificationPublisher dailyNotificationPublisher) {
        this(objectMapper, publisher, postingRepository, dailyNotificationPublisher, Clock.systemUTC());
    }

    ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            JobPostingRepository postingRepository,
            DailyNotificationPublisher dailyNotificationPublisher,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.postingRepository = postingRepository;
        this.dailyNotificationPublisher = dailyNotificationPublisher;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.scrape.completed} event.
     *
     * <ol>
     *   <li>Deserialize JSON into {@link ScrapeCompletedEvent}</li>
     *   <li>Build one {@link NormalizedJobPosting} per scraped job</li>
     *   <li>Classify each against the stored document, then publish it</li>
     *   <li>Publish one {@link DailyNotificationRequestedEvent} listing all postings</li>
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

        for (ScrapedJob job : resolveJobs(event)) {
            String id = NormalizedJobPosting.buildId(event.companyId(), job.externalJobId());
            Optional<NormalizedJobPosting> existing = postingRepository.findById(id, event.companyId());

            NormalizedJobPosting posting = toNormalizedPosting(event.companyId(), job, existing, now);
            PersistenceOutcome outcome = classify(posting, existing);

            publisher.publish(posting);

            if (outcome == PersistenceOutcome.NEW) {
                newCount++;
            } else if (outcome == PersistenceOutcome.UPDATED) {
                changedCount++;
            }
            notificationPostings.add(new NotificationPosting(
                    job.externalJobId(), job.title(), job.location(), job.datePosted(), job.url(),
                    job.salaryRange(), outcome));
        }

        dailyNotificationPublisher.publish(new DailyNotificationRequestedEvent(
                event.runId(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)),
                1,
                newCount,
                changedCount,
                // Detecting postings that disappeared (went inactive) needs a reconciliation step
                // against previously-active postings that is not implemented yet.
                0,
                event.errors(),
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
        if (!event.jobs().isEmpty()) {
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
        LocalDate datePosted = job.datePosted() != null
                ? job.datePosted()
                : LocalDate.now(clock.withZone(ZoneOffset.UTC));
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
                ContentHasher.sha256(job.title() + "|" + job.location() + "|" + job.datePosted()),
                SCHEMA_VERSION,
                job.url()
        );
    }
}

