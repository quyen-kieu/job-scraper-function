package com.jobscraper.scraper.companies.frostbank;

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
public class FrostBankScraper extends WorkdayScraper {
    public FrostBankScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                            ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "frost-bank",
                "https://frostbank.wd5.myworkdayjobs.com", "/wday/cxs/frostbank/External",
                "/External", Pattern.compile("_(R\\d+)(?:-\\d+)?$"),
                () -> Map.of(
                        "jobFamily", List.of(requireFacet(properties.getFrostBankJobFamilyFacetId())),
                        "locationHierarchy1", requireFacets(properties.getFrostBankLocationFacetIds())));
    }
}
