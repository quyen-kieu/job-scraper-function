package com.jobscraper.scraper.companies.humana;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.function.JobScraperProperties;
import com.jobscraper.infrastructure.storage.RawScrapeStorage;
import com.jobscraper.scraper.api.JobSearchHttpClient;
import com.jobscraper.scraper.companies.workday.WorkdayScraper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class HumanaScraper extends WorkdayScraper {
    public HumanaScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                         ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "humana",
                "https://humana.wd5.myworkdayjobs.com", "/wday/cxs/humana/Humana_External_Career_Site",
                "/Humana_External_Career_Site", Pattern.compile("_(R-\\d+)(?:-\\d+)?$"),
                () -> Map.of("jobFamilyGroup", requireFacets(properties.getHumanaJobFamilyFacetIds())));
    }
}
