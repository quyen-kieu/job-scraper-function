package com.jobscraper.scraper.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchScanTrackerTest {
    @Test
    void requiresAnEndAndEveryDistinctJob() {
        SearchScanTracker tracker = new SearchScanTracker();
        tracker.recordPage(2, false);
        assertThat(tracker.isComplete(2)).isFalse();
        tracker.recordPage(2, true);
        assertThat(tracker.isComplete(1)).isFalse();
        assertThat(tracker.isComplete(2)).isTrue();
    }

    @Test
    void rejectsChangingOrInvalidTotals() {
        SearchScanTracker changing = new SearchScanTracker();
        changing.recordPage(2, false);
        changing.recordPage(3, true);
        assertThat(changing.isComplete(2)).isFalse();

        SearchScanTracker invalid = new SearchScanTracker();
        invalid.recordPage(-1, true);
        assertThat(invalid.isComplete(-1)).isFalse();
    }

    @Test
    void acceptsExplicitEmptyResult() {
        SearchScanTracker tracker = new SearchScanTracker();
        tracker.recordPage(0, true);
        assertThat(tracker.isComplete(0)).isTrue();
    }
}
