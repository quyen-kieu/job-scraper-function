package com.jobscraper.scraper.companies.cvs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage;
import com.jobscraper.infrastructure.http.JavaHttpJobSearchClient;
import com.jobscraper.scraper.api.JobDescriptionText;
import com.jobscraper.scraper.api.ScrapeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_LIVE_CVS", matches = "true")
class LiveCvsScraperTest {
    @Test
    void scrapesFilteredJobsAndTheirDescriptions(@TempDir Path tempDir) throws Exception {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setMaxSearchPagesPerRun(0);
        RawStorageProperties storageProperties = new RawStorageProperties();
        storageProperties.setFileRootPath(tempDir.toString());
        var storage = new LocalFileRawScrapeStorage(storageProperties);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var scraper = new CvsScraper(new JavaHttpJobSearchClient(properties), storage, mapper, properties);

        var result = scraper.scrape(new ScrapeContext(UUID.randomUUID().toString(), "cvs", Instant.now()));

        System.out.printf("CVS live scrape: pages=%d details=%d jobs=%d errors=%d%n",
                result.searchPagesFetched(), result.detailPagesFetched(), result.jobs().size(), result.errors().size());
        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).isNotEmpty();
        assertThat(result.searchPagesFetched()).isGreaterThanOrEqualTo(1);
        int reportedTotal = mapper.readTree(Files.readString(Path.of(result.blobPaths().get(0)))).path("total").intValue();
        assertThat(result.jobs()).hasSize(reportedTotal);
        assertThat(result.detailPagesFetched()).isEqualTo(result.jobs().size());
        for (var job : result.jobs()) {
            assertThat(job.descriptionPath()).isNotBlank();
            assertThat(job.descriptionFormat()).isNotNull();
            String description = storage.readDescription("cvs", job.externalJobId(), job.descriptionPath());
            assertThat(JobDescriptionText.fromHtml(description)).isNotBlank();
        }
    }
}
