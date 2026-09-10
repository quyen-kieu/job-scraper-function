package com.jobscraper.domain.job;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes a raw job title for aggregation.
 */
public final class TitleNormalizer {

    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\([^)]*\\)");

    private TitleNormalizer() {
        // Non-instantiable utility class
    }

    /**
     * Normalizes a raw title by stripping parenthetical suffixes and level words.
     *
     * @param titleRaw raw title text
     * @return normalized title (or the trimmed original if stripping would produce blank text)
     */
    public static String normalize(String titleRaw) {
        Objects.requireNonNull(titleRaw, "titleRaw must not be null");

        String normalized = PARENTHETICAL_PATTERN.matcher(titleRaw).replaceAll(" ");

        for (String keyword : JobLevelKeywords.ORDERED_KEYWORDS) {
            normalized = normalized.replaceAll("(?i)\\b" + Pattern.quote(keyword) + "\\b", " ");
        }

        normalized = normalized.replaceAll("\\s+", " ").trim();

        if (normalized.isBlank()) {
            return titleRaw.trim();
        }

        return normalized;
    }
}

