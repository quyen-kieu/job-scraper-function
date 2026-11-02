package com.jobscraper.scraper.api;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a human-readable salary range (e.g. {@code $196,100 - $326,900} or
 * {@code $22.25/hr}) from the free-text description of a job detail page.
 *
 * <p>Company sites rarely populate structured salary data (McKesson's JSON-LD
 * {@code baseSalary} is empty, verified 2026-08-23), so the range is read from the
 * description text itself. Resolution order:</p>
 * <ol>
 *   <li>a {@code $X - $Y} range that follows a "pay range" / "salary range" label;</li>
 *   <li>the first {@code $X - $Y} range anywhere in the description;</li>
 *   <li>the first single rate with an explicit unit ({@code $17.40 per hour}, {@code $22.25/hr}),
 *       skipping amounts that describe incentives, bonuses, or shift differentials.</li>
 * </ol>
 */
public final class SalaryRangeExtractor {

    private static final String DESCRIPTION_SELECTOR = ".ats-description";

    private static final String AMOUNT = "\\$\\s?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?(?:\\s?[kK]\\b)?";
    private static final String UNIT = "(?:\\s*(?:/\\s*|per\\s+|an\\s+|a\\s+)(?:hour|hr|year|yr|annum|annually|month)\\b)";
    private static final String RANGE = AMOUNT + "\\s*(?:-|\u2013|\u2014|to)\\s*" + AMOUNT + UNIT + "?";

    private static final Pattern LABELLED_RANGE = Pattern.compile(
            "(?:pay|salary|compensation|wage)\\s+range[^$]{0,80}?(" + RANGE + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_RANGE = Pattern.compile(RANGE, Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_RATE = Pattern.compile(AMOUNT + UNIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_BASE_PAY_BEFORE = Pattern.compile(
            "incentive|bonus|differential|overnight|sign-?on|referral|up to", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_BASE_PAY_AFTER = Pattern.compile(
            "^\\W*(?:in\\s+)?(?:incentive|bonus|differential|shift|sign-?on|referral)", Pattern.CASE_INSENSITIVE);
    private static final int CONTEXT_WINDOW = 30;

    private SalaryRangeExtractor() {
        // Non-instantiable utility class
    }

    /**
     * Extracts the salary range from a job detail page.
     *
     * @param html the raw detail page HTML
     * @return the salary range as displayed (whitespace-normalized), or empty if none was found
     */
    public static Optional<String> extract(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document doc = Jsoup.parse(html);
        Element description = doc.selectFirst(DESCRIPTION_SELECTOR);
        String text = description != null ? description.text() : doc.body().text();
        return extractFromText(text);
    }

    /**
     * Extracts the salary range from plain description text.
     *
     * @param text the description text
     * @return the salary range, or empty if none was found
     */
    public static Optional<String> extractFromText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher labelled = LABELLED_RANGE.matcher(text);
        if (labelled.find()) {
            return Optional.of(normalize(labelled.group(1)));
        }

        Matcher range = ANY_RANGE.matcher(text);
        if (range.find()) {
            return Optional.of(normalize(range.group()));
        }

        Matcher rate = SINGLE_RATE.matcher(text);
        while (rate.find()) {
            if (!isNonBasePay(text, rate.start(), rate.end())) {
                return Optional.of(normalize(rate.group()));
            }
        }
        return Optional.empty();
    }

    private static boolean isNonBasePay(String text, int start, int end) {
        String before = currentClause(text.substring(Math.max(0, start - CONTEXT_WINDOW), start));
        String after = text.substring(end, Math.min(text.length(), end + CONTEXT_WINDOW));
        return NON_BASE_PAY_BEFORE.matcher(before).find() || NON_BASE_PAY_AFTER.matcher(after).find();
    }

    /** Trims the look-behind window to the clause the amount belongs to (after the last ". ", "! ", "; " or ", "). */
    private static String currentClause(String before) {
        int cut = -1;
        for (String separator : new String[] {". ", "! ", "; ", ", "}) {
            cut = Math.max(cut, before.lastIndexOf(separator));
        }
        return cut >= 0 ? before.substring(cut + 2) : before;
    }

    private static String normalize(String value) {
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}




