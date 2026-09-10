package com.jobscraper.domain.job;

import java.util.List;

/**
 * Ordered job-level keywords used by {@link JobLevelClassifier}.
 *
 * <p>The order matters: more senior/specific levels should be checked first so a title like
 * "Senior Lead Engineer" is classified as "Lead" only if "Senior" is intentionally lower
 * priority in this list.</p>
 */
public final class JobLevelKeywords {

    private JobLevelKeywords() {
        // Non-instantiable constants class
    }

    public static final List<String> ORDERED_KEYWORDS = List.of(
            "Principal",
            "Staff",
            "Lead",
            "Senior",
            "Director",
            "Vice President",
            "VP",
            "Manager",
            "Associate",
            "Junior",
            "Entry-Level",
            "Entry"
    );

    public static final String DEFAULT_LEVEL = "Mid-Level";
}

