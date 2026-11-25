package com.jobscraper.scraper.companies.cvs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.companies.workday.WorkdayScraper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CvsScraper extends WorkdayScraper {
    public CvsScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                      ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "cvs",
                "https://cvshealth.wd1.myworkdayjobs.com", "/wday/cxs/cvshealth/CVS_Health_Careers",
                "/CVS_Health_Careers", Pattern.compile("_(R\\d+)$"),
                () -> Map.of(
                        "jobFamilyGroup", List.of(requireFacet(properties.getCvsJobFamilyFacetId())),
                        "locations", requireFacets(properties.getCvsLocationFacetIds())));
    }
}
