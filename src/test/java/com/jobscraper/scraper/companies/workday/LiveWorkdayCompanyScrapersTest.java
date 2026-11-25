package com.jobscraper.scraper.companies.workday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jobscraper.configuration.RawStorageProperties;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage;
import com.jobscraper.infrastructure.http.JavaHttpJobSearchClient;
import com.jobscraper.scraper.api.JobDescriptionText;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.companies.citi.CitiScraper;
import com.jobscraper.scraper.companies.fanniemae.FannieMaeScraper;
import com.jobscraper.scraper.companies.frostbank.FrostBankScraper;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_LIVE_WORKDAY", matches = "true")
class LiveWorkdayCompanyScrapersTest {
    @ParameterizedTest
    @ValueSource(strings = {"frost-bank", "citi", "fannie-mae"})
    void scrapesFilteredJobsAndTheirDescriptions(String company, @TempDir Path tempDir) throws Exception {
        JobScraperProperties properties = new JobScraperProperties();
        RawStorageProperties storageProperties = new RawStorageProperties();
        storageProperties.setFileRootPath(tempDir.toString());
        var storage = new LocalFileRawScrapeStorage(storageProperties);
        var mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var client = new JavaHttpJobSearchClient(properties);
        WorkdayScraper scraper = switch (company) {
            case "frost-bank" -> new FrostBankScraper(client, storage, mapper, properties);
            case "citi" -> new CitiScraper(client, storage, mapper, properties);
            case "fannie-mae" -> new FannieMaeScraper(client, storage, mapper, properties);
            default -> throw new IllegalArgumentException("Unexpected company: " + company);
        };

        var result = scraper.scrape(new ScrapeContext(UUID.randomUUID().toString(), company, Instant.now()));

        System.out.printf("%s live scrape: pages=%d details=%d jobs=%d errors=%d%n",
                company, result.searchPagesFetched(), result.detailPagesFetched(), result.jobs().size(),
                result.errors().size());
        assertThat(result.errors()).isEmpty();
        assertThat(result.jobs()).isNotEmpty();
        assertThat(result.searchPagesFetched()).isBetween(1, properties.maxSearchPagesPerRun());
        assertThat(result.detailPagesFetched()).isEqualTo(result.jobs().size());
        for (var job : result.jobs()) {
            assertThat(job.descriptionPath()).isNotBlank();
            assertThat(job.descriptionFormat()).isNotNull();
            String description = storage.readDescription(company, job.externalJobId(), job.descriptionPath());
            assertThat(JobDescriptionText.fromHtml(description)).isNotBlank();
        }
    }
}
