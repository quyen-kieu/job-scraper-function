package com.jobscraper.application.notification;

import com.jobscraper.application.persistence.PersistenceOutcome;

import java.util.List;

/**
 * Renders a {@link DailyNotificationRequestedEvent} as plain-text and HTML email bodies, listing
 * every scraped posting grouped as new, changed, and unchanged.
 */
final class DailyNotificationFormatter {

    private static final String NL = "\n";

    private DailyNotificationFormatter() {
    }

    static String toText(DailyNotificationRequestedEvent event) {
        StringBuilder body = new StringBuilder();
        if (event.companyId() != null) {
            body.append("Company: ").append(event.companyId()).append(NL);
        }
        body.append("Run ID: ").append(event.runId()).append(NL);
        body.append("Summary date: ").append(event.summaryDate()).append(NL);
        body.append("Postings scraped: ").append(event.postings().size()).append(NL);
        body.append("New postings: ").append(event.newCount()).append(NL);
        body.append("Changed postings: ").append(event.changedCount()).append(NL);
        body.append("Inactive postings: ").append(event.inactiveCount()).append(NL);

        for (Section section : Section.values()) {
            List<NotificationPosting> postings = section.filter(event);
            if (postings.isEmpty()) {
                continue;
            }
            body.append(NL).append(section.heading).append(" (").append(postings.size()).append("):").append(NL);
            for (NotificationPosting posting : postings) {
                body.append("  - ").append(posting.title()).append(" | ").append(posting.location());
                if (posting.datePosted() != null) {
                    body.append(" | posted ").append(posting.datePosted());
                }
                body.append(NL);
                if (posting.url() != null) {
                    body.append("    ").append(posting.url()).append(NL);
                }
            }
        }

        body.append(NL).append("Failures (").append(event.failures().size()).append("):").append(NL);
        for (String failure : event.failures()) {
            body.append("  - ").append(failure).append(NL);
        }
        return body.toString();
    }

    static String toHtml(DailyNotificationRequestedEvent event) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#222\">");
        html.append("<h2 style=\"margin:0 0 8px\">Job postings");
        if (event.companyId() != null) {
            html.append(" &ndash; ").append(escape(event.companyId()));
        }
        html.append("</h2>");
        html.append("<p style=\"margin:0 0 16px;color:#555\">")
                .append(escape(String.valueOf(event.summaryDate()))).append(" &middot; ")
                .append(event.postings().size()).append(" scraped &middot; ")
                .append(event.newCount()).append(" new &middot; ")
                .append(event.changedCount()).append(" changed")
                .append("</p>");

        if (event.postings().isEmpty()) {
            html.append("<p>No postings were scraped in this run.</p>");
        }

        for (Section section : Section.values()) {
            List<NotificationPosting> postings = section.filter(event);
            if (postings.isEmpty()) {
                continue;
            }
            html.append("<h3 style=\"margin:16px 0 6px\">").append(section.heading)
                    .append(" (").append(postings.size()).append(")</h3>");
            html.append("<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%\">");
            html.append("<tr style=\"background:#f2f2f2;text-align:left\">")
                    .append("<th>Title</th><th>Location</th><th>Posted</th></tr>");
            for (NotificationPosting posting : postings) {
                html.append("<tr style=\"border-bottom:1px solid #e5e5e5\"><td>");
                if (isHttpUrl(posting.url())) {
                    html.append("<a href=\"").append(escape(posting.url())).append("\">")
                            .append(escape(posting.title())).append("</a>");
                } else {
                    html.append(escape(posting.title()));
                }
                html.append("</td><td>").append(escape(posting.location())).append("</td><td>")
                        .append(posting.datePosted() == null ? "" : escape(posting.datePosted().toString()))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }

        if (!event.failures().isEmpty()) {
            html.append("<h3 style=\"margin:16px 0 6px;color:#b00020\">Failures (")
                    .append(event.failures().size()).append(")</h3><ul>");
            for (String failure : event.failures()) {
                html.append("<li>").append(escape(failure)).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<p style=\"margin-top:16px;color:#888;font-size:12px\">Run ID: ")
                .append(escape(event.runId())).append("</p></div>");
        return html.toString();
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private enum Section {
        NEW("New postings", PersistenceOutcome.NEW),
        CHANGED("Changed postings", PersistenceOutcome.UPDATED),
        UNCHANGED("Previously seen postings", PersistenceOutcome.UNCHANGED);

        private final String heading;
        private final PersistenceOutcome outcome;

        Section(String heading, PersistenceOutcome outcome) {
            this.heading = heading;
            this.outcome = outcome;
        }

        List<NotificationPosting> filter(DailyNotificationRequestedEvent event) {
            return event.postings().stream().filter(p -> p.outcome() == outcome).toList();
        }
    }
}




