package com.jobscraper.application.command;

import java.time.Instant;
import java.util.Objects;

public record ScrapeCompanyCommand(String runId, String companyId, Instant requestedAt) {

    public ScrapeCompanyCommand {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");

        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }

        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }
    }
}

