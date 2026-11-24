package com.jobscraper.scraper.api;

import org.jsoup.Jsoup;

public final class JobDescriptionText {
    private JobDescriptionText() {
    }

    public static String fromHtml(String html) {
        return html == null ? "" : Jsoup.parseBodyFragment(html).text();
    }
}
