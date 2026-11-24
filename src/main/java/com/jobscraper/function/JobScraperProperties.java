package com.jobscraper.function;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "job-scraper")
public class JobScraperProperties {

    private String userAgent = "job-scraper/0.1";
    private Duration requestTimeout = Duration.ofSeconds(30);
    private int maxRetries = 3;
    private boolean enabled = true;
    private List<CompanyProperties> companies = new ArrayList<>();
    private int maxSearchPagesPerRun = 3;
    private Duration requestDelay = Duration.ofMillis(300);
    private String mckessonStateFacetId = "fc77e3a1ab36487f9646d14f7242dd77";
    private String mckessonJobFamilyFacetId = "29ce91b0412201aafb7bf4bd4f169117";
    private String mckessonCountryFacetId = "bc33aa3152ec42d4995f4791a106ed09";

    public String getMckessonStateFacetId() {
        return mckessonStateFacetId;
    }

    public void setMckessonStateFacetId(String id) {
        this.mckessonStateFacetId = id;
    }

    public String getMckessonJobFamilyFacetId() {
        return mckessonJobFamilyFacetId;
    }

    public void setMckessonJobFamilyFacetId(String id) {
        this.mckessonJobFamilyFacetId = id;
    }

    public String getMckessonCountryFacetId() {
        return mckessonCountryFacetId;
    }

    public void setMckessonCountryFacetId(String id) {
        this.mckessonCountryFacetId = id;
    }

    public String userAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<CompanyProperties> companies() {
        return companies;
    }

    public void setCompanies(List<CompanyProperties> companies) {
        this.companies = companies == null ? new ArrayList<>() : companies;
    }

    public List<CompanyProperties> enabledCompanies() {
        return companies.stream()
                .filter(CompanyProperties::enabled)
                .filter(company -> !company.id().isBlank())
                .toList();
    }

    public int maxSearchPagesPerRun() {
        return maxSearchPagesPerRun;
    }

    public void setMaxSearchPagesPerRun(int maxSearchPagesPerRun) {
        this.maxSearchPagesPerRun = maxSearchPagesPerRun;
    }

    public Duration requestDelay() {
        return requestDelay;
    }

    public void setRequestDelay(Duration requestDelay) {
        this.requestDelay = requestDelay == null ? Duration.ofMillis(300) : requestDelay;
    }

    public static final class CompanyProperties {
        private String id = "";
        private boolean enabled = true;

        public String id() {
            return id;
        }

        public void setId(String id) {
            this.id = Objects.requireNonNullElse(id, "");
        }

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
