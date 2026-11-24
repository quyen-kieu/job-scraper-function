package com.jobscraper.scraper.api;

import com.jobscraper.domain.job.DescriptionFormat;

public record DescriptionReference(String path, DescriptionFormat format) {
}
