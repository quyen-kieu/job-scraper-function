package com.jobscraper.scraper.companies.capitalone;

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
public class CapitalOneScraper extends WorkdayScraper {
    public CapitalOneScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                             ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "capital-one",
                "https://capitalone.wd12.myworkdayjobs.com", "/wday/cxs/capitalone/Capital_One",
                "/Capital_One", Pattern.compile("_(R\\d+)(?:-\\d+)?$"),
                () -> Map.of(
                        "workerSubType", List.of(requireFacet(properties.getCapitalOneWorkerSubtypeFacetId())),
                        "timeType", List.of(requireFacet(properties.getCapitalOneTimeTypeFacetId())),
                        "locations", requireFacets(properties.getCapitalOneLocationFacetIds()),
                        "jobFamilyGroup", requireFacets(properties.getCapitalOneJobFamilyFacetIds())));
    }
}
