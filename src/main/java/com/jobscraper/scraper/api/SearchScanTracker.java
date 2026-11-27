package com.jobscraper.scraper.api;

/**
 * Conservatively identifies searches that reached a stable reported total without
 * duplicate or missing job IDs. This cannot guarantee a source-side snapshot.
 */
public final class SearchScanTracker {
    private Integer total;
    private boolean consistent = true;
    private boolean reachedEnd;

    public void recordPage(int reportedTotal, boolean lastPage) {
        if (reportedTotal < 0 || (total != null && total != reportedTotal)) {
            consistent = false;
        }
        if (total == null) {
            total = reportedTotal;
        }
        reachedEnd = lastPage;
    }

    public boolean isComplete(int uniqueJobs) {
        return consistent && reachedEnd && total != null && total == uniqueJobs;
    }
}
