package com.jobscraper.application.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.notification.DailyNotificationPublisher;
import com.jobscraper.application.notification.RunCompletionAggregator;
import com.jobscraper.domain.job.ContentHasher;
import com.jobscraper.domain.job.JobLevelClassifier;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.domain.job.TitleNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application service that deserializes a {@code job.scrape.completed} message,
 * derives normalized job postings, and publishes each to {@code job.postings.normalized}.
 *
 * <p><b>Stage 5 scope:</b> normalization rules are now applied here so downstream metrics
 * aggregation can consume stable {@code titleNormalized} and {@code jobLevel} values without
 * duplicating rule logic in the metrics consumer.</p>
 *
 * <p>The external job ID for each posting is derived from the {@code job-{externalJobId}.html}
 * blob path naming convention established in Stage 3's {@code AzureBlobRawScrapeStorage}.
 * Detail-page content (title, location) is not yet fetched back from Blob Storage in this
 * stage, so those fields use placeholder values until the scraper's fetch/parse logic
 * (Stage 3's deferred {@code McKessonScraper} business logic) is completed.</p>
 *
 * <p><b>Path handling:</b> blob paths may use either {@code /}-separated Azure Blob keys or
 * OS-native absolute filesystem paths (e.g. Windows backslash paths from
 * {@code LocalFileRawScrapeStorage} in local-dev mode). Only the final path segment (the file
 * name) is matched against the {@code job-{externalJobId}.html} pattern; the file name is
 * extracted by splitting on both {@code /} and {@code \} separators first, so directory
 * components that happen to contain the literal substring {@code "job-"} (such as a project
 * folder named {@code job-scraper-function}) can never be mistaken for the job ID.</p>
 */
@Service
public class ScrapeCompletedNormalizationHandler {

    private static final Pattern JOB_DETAIL_FILE_NAME_PATTERN = Pattern.compile("^job-(.+)\\.html$");
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final NormalizedPostingPublisher publisher;
    private final RunCompletionAggregator runCompletionAggregator;
    private final DailyNotificationPublisher dailyNotificationPublisher;
    private final Clock clock;

    @Autowired
    public ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            RunCompletionAggregator runCompletionAggregator,
            DailyNotificationPublisher dailyNotificationPublisher) {
        this(objectMapper, publisher, runCompletionAggregator, dailyNotificationPublisher, Clock.systemUTC());
    }

    ScrapeCompletedNormalizationHandler(
            ObjectMapper objectMapper,
            NormalizedPostingPublisher publisher,
            RunCompletionAggregator runCompletionAggregator,
            DailyNotificationPublisher dailyNotificationPublisher,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.runCompletionAggregator = runCompletionAggregator;
        this.dailyNotificationPublisher = dailyNotificationPublisher;
        this.clock = clock;
    }

    /**
     * Handles a single {@code job.scrape.completed} event.
     *
     * <p>Steps:
     * <ol>
     *   <li>Deserialize JSON into {@link ScrapeCompletedEvent}</li>
     *   <li>Derive one {@link NormalizedJobPosting} per detail-page blob path discovered</li>
     *   <li>Publish each posting via {@link NormalizedPostingPublisher}</li>
     *   <li>Register the expected posting count with {@link RunCompletionAggregator} (Stage 6),
     *       so a daily notification email can be sent once {@link com.jobscraper.function.PersistenceConsumerFunction}
     *       has persisted every posting from this scrape</li>
     * </ol>
     *
     * @param json the raw JSON message
     * @return the number of postings published
     * @throws Exception any deserialization or publishing failure (triggers redelivery)
     */
    public int handle(String json) throws Exception {
        ScrapeCompletedEvent event = objectMapper.readValue(json, ScrapeCompletedEvent.class);

        int publishedCount = 0;
        for (String blobPath : event.blobPaths()) {
            String externalJobId = extractExternalJobId(blobPath);
            if (externalJobId == null) {
                continue;
            }

            NormalizedJobPosting posting = toNormalizedPosting(event, externalJobId);
            publisher.publish(posting);
            publishedCount++;
        }

        runCompletionAggregator
                .registerExpectedCompany(event.runId(), event.companyId(), publishedCount, event.errors())
                .ifPresent(dailyNotificationPublisher::publish);

        return publishedCount;
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


    private NormalizedJobPosting toNormalizedPosting(ScrapeCompletedEvent event, String externalJobId) {
        Instant now = Instant.now(clock);
        LocalDate datePosted = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        // Title extraction is still placeholder until detail HTML parsing is completed.
         String titleRaw = "Unknown title (pending detail-page fetch): " + externalJobId;
         String titleNormalized = TitleNormalizer.normalize(titleRaw);
         String jobLevel = JobLevelClassifier.classify(titleRaw);

         return new NormalizedJobPosting(
                 NormalizedJobPosting.buildId(event.companyId(), externalJobId),
                 event.companyId(),
                 externalJobId,
                 titleRaw,
                 titleNormalized,
                 jobLevel,
                 "Unknown",
                 datePosted,
                 now,
                 now,
                 true,
                 ContentHasher.sha256(titleRaw),
                 SCHEMA_VERSION
         );
    }
}
