package com.jobscraper.scraper.companies.oracle;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one Oracle Recruiting Cloud (Oracle HCM "Candidate Experience") career site and the
 * keyword search to run against it, and builds the public REST and job-page URLs for that site.
 *
 * <p>Candidate Experience sites are JavaScript single-page apps; the page at
 * {@code https://{host}/hcmUI/CandidateExperience/en/sites/{siteNumber}/jobs?keyword=...} loads its
 * data from the anonymous, read-only REST endpoints below (verified live on 2026-11-02):</p>
 * <ul>
 *   <li>search: {@code /hcmRestApi/resources/latest/recruitingCEJobRequisitions}
 *       ({@code finder=findReqs;siteNumber=..,limit=..,offset=..,keyword=".."}) &ndash; the
 *       {@code requisitionList} is only returned when {@code expand=requisitionList...} is set;</li>
 *   <li>detail: {@code /hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails}
 *       ({@code finder=ById;Id="..",siteNumber=..}).</li>
 * </ul>
 *
 * @param companyId the internal company ID used in Kafka messages, Blob paths, and Cosmos documents
 * @param host the Oracle Cloud host name, e.g. {@code fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com}
 * @param siteNumber the Candidate Experience site number, e.g. {@code CX_1}
 * @param keyword the search keyword, sent quoted exactly as the career site UI does
 */
public record OracleRecruitingSite(String companyId, String host, String siteNumber, String keyword) {

    private static final Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9.-]+");
    private static final Pattern SITE_NUMBER_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final String REST_PATH = "/hcmRestApi/resources/latest/";

    public OracleRecruitingSite {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(siteNumber, "siteNumber must not be null");
        Objects.requireNonNull(keyword, "keyword must not be null");
        if (companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }
        if (!HOST_PATTERN.matcher(host).matches()) {
            throw new IllegalArgumentException("host must be a bare host name: " + host);
        }
        if (!SITE_NUMBER_PATTERN.matcher(siteNumber).matches()) {
            throw new IllegalArgumentException("siteNumber must be alphanumeric: " + siteNumber);
        }
    }

    /**
     * Builds the search REST URI for one page of results, newest postings first.
     *
     * @param offset zero-based index of the first requisition to return
     * @param limit page size
     * @return the search URI
     */
    public URI searchUri(int offset, int limit) {
        String finder = "findReqs;siteNumber=" + siteNumber
                + ",limit=" + limit
                + ",keyword=" + encode("\"" + keyword + "\"")
                + ",sortBy=POSTING_DATES_DESC"
                + ",offset=" + offset;
        return URI.create("https://" + host + REST_PATH + "recruitingCEJobRequisitions"
                + "?onlyData=true&expand=requisitionList.secondaryLocations&finder=" + finder);
    }

    /**
     * Builds the job-detail REST URI for one requisition.
     *
     * @param requisitionId the requisition {@code Id} from the search results
     * @return the detail URI
     */
    public URI detailUri(String requisitionId) {
        String finder = "ById;Id=" + encode("\"" + requisitionId + "\"") + ",siteNumber=" + siteNumber;
        return URI.create("https://" + host + REST_PATH + "recruitingCEJobRequisitionDetails"
                + "?expand=all&onlyData=true&finder=" + finder);
    }

    /**
     * Builds the public, human-facing job page URL (used as the link in the daily email).
     *
     * @param requisitionId the requisition {@code Id}
     * @return the public job page URL
     */
    public String publicJobUrl(String requisitionId) {
        return "https://" + host + "/hcmUI/CandidateExperience/en/sites/" + siteNumber + "/job/"
                + encode(requisitionId);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

