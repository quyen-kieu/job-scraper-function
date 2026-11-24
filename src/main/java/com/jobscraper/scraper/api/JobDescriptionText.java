package com.jobscraper.scraper.api;

import com.jobscraper.domain.job.DescriptionFormat;
import org.jsoup.Jsoup;

public final class JobDescriptionText {
    private JobDescriptionText() {
    }

    public static String fromHtml(String html) {
        return html == null ? "" : Jsoup.parseBodyFragment(html).text();
    }

    public static DescriptionFormat sourceFormat(String source) {
        return source != null && Jsoup.parseBodyFragment(source).body().childrenSize() > 0
                ? DescriptionFormat.HTML : DescriptionFormat.PLAIN_TEXT;
    }
}
