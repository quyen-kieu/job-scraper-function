package com.jobscraper.scraper.companies.mckesson;

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
public class McKessonScraper extends WorkdayScraper {
    public McKessonScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                           ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "mckesson",
                "https://mckesson.wd3.myworkdayjobs.com", "/wday/cxs/mckesson/External_Careers",
                "/en-US/External_Careers", Pattern.compile("_(JR\\d+)$"),
                () -> Map.of(
                        "Location_Region_State_Province", List.of(requireFacet(properties.getMckessonStateFacetId())),
                        "jobFamilyGroup", List.of(requireFacet(properties.getMckessonJobFamilyFacetId())),
                        "Location_Country", List.of(requireFacet(properties.getMckessonCountryFacetId()))));
    }
}
