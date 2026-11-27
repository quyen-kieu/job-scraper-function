package com.jobscraper.infrastructure.cosmos;

public record ReconciliationResult(boolean completed, int inactiveCount) {
    public static ReconciliationResult skipped() {
        return new ReconciliationResult(false, 0);
    }
}
