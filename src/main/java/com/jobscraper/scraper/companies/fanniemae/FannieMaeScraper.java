package com.jobscraper.scraper.companies.fanniemae;

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
public class FannieMaeScraper extends WorkdayScraper {
    public FannieMaeScraper(JobSearchHttpClient httpClient, RawScrapeStorage storage,
                            ObjectMapper objectMapper, JobScraperProperties properties) {
        super(httpClient, storage, objectMapper, properties, "fannie-mae",
                "https://fanniemae.wd1.myworkdayjobs.com", "/wday/cxs/fanniemae/FannieMaeCareers",
                "/FannieMaeCareers", Pattern.compile("_(JR\\d+)(?:-\\d+)?$"),
                () -> Map.of(
                        "remoteType", List.of(requireFacet(properties.getFannieMaeRemoteTypeFacetId())),
                        "jobFamilyGroup", List.of(requireFacet(properties.getFannieMaeJobFamilyFacetId())),
                        "locations", List.of(requireFacet(properties.getFannieMaeLocationFacetId()))));
    }
}
