package com.jobscraper.scraper.api;

@FunctionalInterface
public interface DetailFetchPolicy {
    boolean shouldFetch(ScrapedJob job, boolean reliableSearchDate);
}
