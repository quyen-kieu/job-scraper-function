package com.jobscraper.scraper.companies.frostbank;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FrostBankPageParser {
    private static final String BASE_URL = "https://careers.frostbank.com";
    private static final Pattern DDO_ASSIGNMENT = Pattern.compile("\\bphApp\\.ddo\\s*=\\s*");

    private final ObjectMapper objectMapper;

    FrostBankPageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    SearchPage parseSearch(String html) throws IOException {
        JsonNode search = parseDdo(html).path("eagerLoadRefineSearch");
        JsonNode total = search.path("totalHits");
        JsonNode entries = search.path("data").path("jobs");
        if (search.path("status").asInt() != 200 || !total.isIntegralNumber()
                || !total.canConvertToInt() || total.intValue() < 0 || !entries.isArray()) {
            throw new IllegalArgumentException("Invalid Frost Bank search response");
        }

        List<Job> jobs = new ArrayList<>();
        for (JsonNode entry : entries) {
            String id = requiredText(entry, "reqId");
            String seq = requiredText(entry, "jobSeqNo");
            String title = requiredText(entry, "title");
            String location = entry.path("location").asText("");
            String posted = entry.path("postedDate").asText("");
            LocalDate postedDate = null;
            try {
                if (posted.length() >= 10) {
                    postedDate = LocalDate.parse(posted.substring(0, 10));
                }
            } catch (DateTimeParseException ignored) {
                // Other scrapers also leave unparseable dates unknown.
            }
            String pathSegment = URLEncoder.encode(seq, StandardCharsets.UTF_8).replace("+", "%20");
            jobs.add(new Job(id, title, location, postedDate, URI.create(BASE_URL + "/us/en/job/" + pathSegment)));
        }
        return new SearchPage(total.intValue(), jobs, search.toString());
    }

    String parseDescription(String html, String expectedId) throws IOException {
        JsonNode detail = parseDdo(html).path("jobDetail");
        JsonNode job = detail.path("data").path("job");
        if (detail.path("status").asInt() != 200 || !expectedId.equals(requiredText(job, "reqId"))
                || !job.path("description").isTextual()) {
            throw new IllegalArgumentException("Invalid Frost Bank job detail for " + expectedId);
        }
        return job.path("description").textValue();
    }

    private JsonNode parseDdo(String html) throws IOException {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Empty Frost Bank page");
        }
        for (Element script : Jsoup.parse(html).select("script")) {
            String data = script.data();
            Matcher matcher = DDO_ASSIGNMENT.matcher(data);
            if (matcher.find()) {
                try (JsonParser parser = objectMapper.getFactory().createParser(data.substring(matcher.end()))) {
                    JsonNode result = objectMapper.readTree(parser);
                    if (result != null && result.isObject()) {
                        return result;
                    }
                    throw new IllegalArgumentException("Invalid Frost Bank embedded data");
                }
            }
        }
        throw new IllegalArgumentException("Missing Frost Bank embedded data");
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing Frost Bank job " + field);
        }
        return value.textValue();
    }

    record SearchPage(int total, List<Job> jobs, String rawSearchJson) {
    }

    record Job(String id, String title, String location, LocalDate postedDate, URI url) {
    }
}
