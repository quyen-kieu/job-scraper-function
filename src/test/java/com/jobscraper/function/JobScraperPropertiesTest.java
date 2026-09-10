package com.jobscraper.function;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobScraperPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void usesSafeDefaultsWhenSettingsAreNotProvided() {
        contextRunner.run(context -> {
            JobScraperProperties properties = context.getBean(JobScraperProperties.class);

            assertThat(properties.userAgent()).isEqualTo("job-scraper/0.1");
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.maxRetries()).isEqualTo(3);
            assertThat(properties.enabled()).isTrue();
        });
    }

    @Test
    void bindsApplicationSettingsToProperties() {
        contextRunner
                .withPropertyValues(
                        "job-scraper.user-agent=portfolio-scraper/1.0",
                        "job-scraper.request-timeout=PT45S",
                        "job-scraper.max-retries=5",
                        "job-scraper.enabled=false",
                        "job-scraper.companies[0].id=usaa",
                        "job-scraper.companies[0].enabled=true",
                        "job-scraper.companies[1].id=oracle",
                        "job-scraper.companies[1].enabled=false")
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);

                    assertThat(properties.userAgent()).isEqualTo("portfolio-scraper/1.0");
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.maxRetries()).isEqualTo(5);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.companies()).hasSize(2);
                    assertThat(properties.enabledCompanies()).extracting(JobScraperProperties.CompanyProperties::id)
                            .containsExactly("usaa");
                });
    }

    @Test
    void enabledCompaniesSkipsBlankIds() {
        JobScraperProperties properties = new JobScraperProperties();

        JobScraperProperties.CompanyProperties companyWithBlankId = new JobScraperProperties.CompanyProperties();
        companyWithBlankId.setId("   ");
        companyWithBlankId.setEnabled(true);

        JobScraperProperties.CompanyProperties validCompany = new JobScraperProperties.CompanyProperties();
        validCompany.setId("mckesson");
        validCompany.setEnabled(true);

        properties.setCompanies(List.of(companyWithBlankId, validCompany));

        assertThat(properties.enabledCompanies()).extracting(JobScraperProperties.CompanyProperties::id)
                .containsExactly("mckesson");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JobScraperProperties.class)
    static class TestConfiguration {
    }
}
