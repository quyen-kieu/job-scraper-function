package com.jobscraper.scraper.companies.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Oracle Recruiting Cloud REST responses (shape verified live on 2026-11-02 against
 * GM Financial's {@code CX_1} site; see {@code src/test/resources/fixtures/gmfinancial/}).
 *
 * <p>Both endpoints wrap their payload in {@code {"items":[{...}]}}. Search results live under
 * {@code items[0].requisitionList[]} with {@code Id}, {@code Title}, {@code PrimaryLocation} and
 * {@code PostedDate}; the total hit count is {@code items[0].TotalJobsCount}. Job details live in
 * {@code items[0]}, with the description split across several HTML string fields.</p>
 */
public final class OracleRecruitingResponseParser {

    /** Detail fields whose HTML together forms the human-readable job description. */
    private static final List<String> DESCRIPTION_FIELDS = List.of(
            "ExternalDescriptionStr",
            "ExternalResponsibilitiesStr",
            "ExternalQualificationsStr",
            "OrganizationDescriptionStr",
            "CorporateDescriptionStr");

    private OracleRecruitingResponseParser() {
        // Non-instantiable utility class
    }

    /**
     * Parses one search-results response.
     *
     * @param objectMapper the JSON mapper
     * @param json the raw response body
     * @return the parsed page; an empty page if the response has no items
     * @throws IOException if the body is not valid JSON
     */
    public static OracleSearchPage parseSearchPage(ObjectMapper objectMapper, String json) throws IOException {
        JsonNode search = objectMapper.readTree(json).path("items").path(0);
        List<OracleRequisitionSummary> requisitions = new ArrayList<>();
        for (JsonNode requisition : search.path("requisitionList")) {
            String id = requisition.path("Id").asText("");
            if (id.isBlank()) {
                continue;
            }
            requisitions.add(new OracleRequisitionSummary(
                    id,
                    requisition.path("Title").asText("").trim(),
                    requisition.path("PrimaryLocation").asText("").trim(),
                    requisition.path("PostedDate").asText("").trim()));
        }
        return new OracleSearchPage(search.path("TotalJobsCount").asInt(0), requisitions);
    }

    /**
     * Extracts the plain-text job description from one job-detail response.
     *
     * @param objectMapper the JSON mapper
     * @param json the raw response body
     * @return the description text (HTML stripped, whitespace collapsed); empty if absent
     * @throws IOException if the body is not valid JSON
     */
    public static String parseDescriptionText(ObjectMapper objectMapper, String json) throws IOException {
        JsonNode detail = objectMapper.readTree(json).path("items").path(0);
        StringBuilder html = new StringBuilder();
        for (String field : DESCRIPTION_FIELDS) {
            JsonNode value = detail.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                html.append("<div>").append(value.asText()).append("</div>");
            }
        }
        return html.isEmpty() ? "" : Jsoup.parse(html.toString()).text();
    }
}

