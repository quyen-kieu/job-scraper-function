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
    private String frostBankJobFamilyFacetId = "3a9cae9f1324015f492308c79dc96d35";
    private List<String> frostBankLocationFacetIds = new ArrayList<>(List.of(
            "8fe73009103a424c89e0c6c010a6d8f3",
            "58c99fe2f997418f8c54b3ebcf1b6a5d"));
    private String citiJobFamilyFacetId = "e32326e1708d01575bddff0c120102c1";
    private String citiWorkerSubtypeFacetId = "e32326e1708d01289a50257249019cdd";
    private String citiCountryFacetId = "bc33aa3152ec42d4995f4791a106ed09";
    private String citiStateFacetId = "fc77e3a1ab36487f9646d14f7242dd77";
    private String fannieMaeRemoteTypeFacetId = "bf1dc34adb121001727a83cb88650000";
    private String fannieMaeJobFamilyFacetId = "bf1dc34adb12100175ce6a6921f70000";
    private String fannieMaeLocationFacetId = "464222325cc3100210ee7e4b71320000";
    private String cvsJobFamilyFacetId = "e65dbadf6a50100168ed86fe4cf50001";
    private List<String> cvsLocationFacetIds = new ArrayList<>(List.of(
            "69b7f7384b5e1008e9c744772f180000",
            "e75b3cfda6d41000edbef9d1f17b0000",
            "e75b3cfda6d41000edbdcbb1c2b50000",
            "e75b3cfda6d41000edbc9e0488440000",
            "e75b3cfda6d41000edc098b35b130000",
            "e75b3cfda6d41000edc32657f9160000"));

    public String getFrostBankJobFamilyFacetId() {
        return frostBankJobFamilyFacetId;
    }

    public void setFrostBankJobFamilyFacetId(String id) {
        this.frostBankJobFamilyFacetId = id;
    }

    public List<String> getFrostBankLocationFacetIds() {
        return frostBankLocationFacetIds;
    }

    public void setFrostBankLocationFacetIds(List<String> ids) {
        this.frostBankLocationFacetIds = ids;
    }

    public String getCitiJobFamilyFacetId() {
        return citiJobFamilyFacetId;
    }

    public void setCitiJobFamilyFacetId(String id) {
        this.citiJobFamilyFacetId = id;
    }

    public String getCitiWorkerSubtypeFacetId() {
        return citiWorkerSubtypeFacetId;
    }

    public void setCitiWorkerSubtypeFacetId(String id) {
        this.citiWorkerSubtypeFacetId = id;
    }

    public String getCitiCountryFacetId() {
        return citiCountryFacetId;
    }

    public void setCitiCountryFacetId(String id) {
        this.citiCountryFacetId = id;
    }

    public String getCitiStateFacetId() {
        return citiStateFacetId;
    }

    public void setCitiStateFacetId(String id) {
        this.citiStateFacetId = id;
    }

    public String getFannieMaeRemoteTypeFacetId() {
        return fannieMaeRemoteTypeFacetId;
    }

    public void setFannieMaeRemoteTypeFacetId(String id) {
        this.fannieMaeRemoteTypeFacetId = id;
    }

    public String getFannieMaeJobFamilyFacetId() {
        return fannieMaeJobFamilyFacetId;
    }

    public void setFannieMaeJobFamilyFacetId(String id) {
        this.fannieMaeJobFamilyFacetId = id;
    }

    public String getFannieMaeLocationFacetId() {
        return fannieMaeLocationFacetId;
    }

    public void setFannieMaeLocationFacetId(String id) {
        this.fannieMaeLocationFacetId = id;
    }

    public String getCvsJobFamilyFacetId() {
        return cvsJobFamilyFacetId;
    }

    public void setCvsJobFamilyFacetId(String id) {
        this.cvsJobFamilyFacetId = id;
    }

    public List<String> getCvsLocationFacetIds() {
        return cvsLocationFacetIds;
    }

    public void setCvsLocationFacetIds(List<String> ids) {
        this.cvsLocationFacetIds = ids;
    }

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
