package com.jobscraper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    private boolean enabled = false;
    private String bootstrapServers = "";
    private String securityProtocol = "SASL_SSL";
    private String saslMechanism = "PLAIN";
    private String username = "";
    private String password = "";
    private String scrapeCommandsTopic = "job.scrape.commands";
    private String scrapeCompletedTopic = "job.scrape.completed";
    private String postingsNormalizedTopic = "job.postings.normalized";
    private String metricsMonthlyTopic = "job.metrics.monthly";
    private String notificationsDailyTopic = "notifications.daily";

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = Objects.requireNonNullElse(bootstrapServers, "");
    }

    public String securityProtocol() {
        return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = Objects.requireNonNullElse(securityProtocol, "SASL_SSL");
    }

    public String saslMechanism() {
        return saslMechanism;
    }

    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = Objects.requireNonNullElse(saslMechanism, "PLAIN");
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = Objects.requireNonNullElse(username, "");
    }

    public String password() {
        return password;
    }

    public void setPassword(String password) {
        this.password = Objects.requireNonNullElse(password, "");
    }

    public String scrapeCommandsTopic() {
        return scrapeCommandsTopic;
    }

    public void setScrapeCommandsTopic(String scrapeCommandsTopic) {
        this.scrapeCommandsTopic = Objects.requireNonNullElse(scrapeCommandsTopic, "job.scrape.commands");
    }

    public String scrapeCompletedTopic() {
        return scrapeCompletedTopic;
    }

    public void setScrapeCompletedTopic(String scrapeCompletedTopic) {
        this.scrapeCompletedTopic = Objects.requireNonNullElse(scrapeCompletedTopic, "job.scrape.completed");
    }

    public String postingsNormalizedTopic() {
        return postingsNormalizedTopic;
    }

    public void setPostingsNormalizedTopic(String postingsNormalizedTopic) {
        this.postingsNormalizedTopic = Objects.requireNonNullElse(postingsNormalizedTopic, "job.postings.normalized");
    }

    public String metricsMonthlyTopic() {
        return metricsMonthlyTopic;
    }

    public void setMetricsMonthlyTopic(String metricsMonthlyTopic) {
        this.metricsMonthlyTopic = Objects.requireNonNullElse(metricsMonthlyTopic, "job.metrics.monthly");
    }

    public String notificationsDailyTopic() {
        return notificationsDailyTopic;
    }

    public void setNotificationsDailyTopic(String notificationsDailyTopic) {
        this.notificationsDailyTopic = Objects.requireNonNullElse(notificationsDailyTopic, "notifications.daily");
    }
}
