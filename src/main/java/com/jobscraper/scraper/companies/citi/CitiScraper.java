package com.jobscraper.scraper.companies.citi;

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
public class CitiScraper extends WorkdayScraper {
    public CitiScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                       ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "citi",
                "https://citi.wd5.myworkdayjobs.com", "/wday/cxs/citi/2", "/2",
                Pattern.compile("_(\\d+)(?:-\\d+)?$"),
                () -> Map.of(
                        "jobFamilyGroup", List.of(requireFacet(properties.getCitiJobFamilyFacetId())),
                        "workerSubType", List.of(requireFacet(properties.getCitiWorkerSubtypeFacetId())),
                        "Country_and_Jurisdiction", List.of(requireFacet(properties.getCitiCountryFacetId())),
                        "State_or_Province", List.of(requireFacet(properties.getCitiStateFacetId()))));
    }
}
