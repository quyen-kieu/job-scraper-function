package com.jobscraper.domain.job;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Classifies a job level from a raw title.
 */
public final class JobLevelClassifier {

    private JobLevelClassifier() {
        // Non-instantiable utility class
    }

    /**
     * Derives a job level using ordered whole-word keyword matching.
     *
     * @param titleRaw the original job title
     * @return canonical job level, or {@link JobLevelKeywords#DEFAULT_LEVEL} when no match exists
     */
    public static String classify(String titleRaw) {
        Objects.requireNonNull(titleRaw, "titleRaw must not be null");

        for (String keyword : JobLevelKeywords.ORDERED_KEYWORDS) {
            Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b");
            if (pattern.matcher(titleRaw).find()) {
                return keyword;
            }
        }

        return JobLevelKeywords.DEFAULT_LEVEL;
    }
}

