package com.jobscraper.application.command;

public interface ScrapeCommandPublisher {

    void publish(ScrapeCompanyCommand command);
}

