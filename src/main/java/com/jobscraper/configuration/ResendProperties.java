package com.jobscraper.configuration;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Resend daily notification sender.
 *
 * <p>All fields are injected from environment variables or Spring configuration,
 * never hardcoded. The API key must never be logged or committed to source control.</p>
 */
@ConfigurationProperties(prefix = "resend")
public class ResendProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String fromEmail = "";
    private String fromName = "Job Scraper";
    private String toEmail = "";
    private String subjectPrefix = "Job Scraper Daily Summary";

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String apiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = Objects.requireNonNullElse(apiKey, "");
    }

    public String fromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = Objects.requireNonNullElse(fromEmail, "");
    }

    public String fromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = Objects.requireNonNullElse(fromName, "");
    }

    public String toEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = Objects.requireNonNullElse(toEmail, "");
    }

    public String subjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = Objects.requireNonNullElse(subjectPrefix, "");
    }
}

