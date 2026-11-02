package com.jobscraper.scraper.companies.gmfinancial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.blob.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.companies.oracle.OracleRecruitingScraper;
import com.jobscraper.scraper.companies.oracle.OracleRecruitingSite;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the GM Financial scraper: a keyword search for "software development" on GM Financial's
 * Oracle Recruiting Cloud careers site
 * ({@code https://fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1/jobs?keyword=software%20development}).
 *
 * <p>The scraper is picked up by {@code CompanyScraperRegistry} like any other
 * {@code CompanyScraper}; it only runs when {@code gm-financial} is listed and enabled in
 * {@code job-scraper.companies} (app setting {@code JOB_SCRAPER_COMPANIES_<n>_ID}).</p>
 */
@Configuration
public class GmFinancialScraperConfiguration {

    public static final String COMPANY_ID = "gm-financial";
    static final String HOST = "fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com";
    static final String SITE_NUMBER = "CX_1";
    static final String KEYWORD = "software development";

    @Bean
    public OracleRecruitingScraper gmFinancialScraper(
            JobSearchHttpClient httpClient,
            RawScrapeStorage storage,
            ObjectMapper objectMapper,
            JobScraperProperties properties) {
        return new OracleRecruitingScraper(
                new OracleRecruitingSite(COMPANY_ID, HOST, SITE_NUMBER, KEYWORD),
                httpClient, storage, objectMapper, properties);
    }
}

