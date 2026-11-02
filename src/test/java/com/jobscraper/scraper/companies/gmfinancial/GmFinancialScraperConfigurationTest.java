package com.jobscraper.scraper.companies.gmfinancial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.CompanyScraper;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.registry.CompanyScraperRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GmFinancialScraperConfigurationTest {

    @Test
    void shouldRegisterGmFinancialScraperInRegistry() {
        CompanyScraper scraper = new GmFinancialScraperConfiguration().gmFinancialScraper(
                mock(JobSearchHttpClient.class), mock(RawScrapeStorage.class), new ObjectMapper(),
                new JobScraperProperties());

        CompanyScraperRegistry registry = new CompanyScraperRegistry(List.of(scraper));

        assertThat(scraper.companyId()).isEqualTo("gm-financial");
        assertThat(registry.find("gm-financial")).containsSame(scraper);
    }
}

