package com.jobscraper.function;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void pageLimitCanBePositiveOrUnlimitedButNotNegative() {
        JobScraperProperties properties = new JobScraperProperties();
        assertThat(properties.canFetchSearchPage(100)).isTrue();
        properties.setMaxSearchPagesPerRun(3);
        assertThat(properties.canFetchSearchPage(2)).isTrue();
        assertThat(properties.canFetchSearchPage(3)).isFalse();
        properties.setMaxSearchPagesPerRun(0);
        assertThat(properties.canFetchSearchPage(100)).isTrue();
        assertThatThrownBy(() -> properties.setMaxSearchPagesPerRun(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or positive");
    }

    @Test
    void bindsUnlimitedPageLimitFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_MAX_SEARCH_PAGES_PER_RUN", "0"))))
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);
                    assertThat(properties.maxSearchPagesPerRun()).isZero();
                    assertThat(properties.canFetchSearchPage(100)).isTrue();
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

    @Test
    void bindsMcKessonFacetIdsFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_MCKESSON_STATE_FACET_ID", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "JOB_SCRAPER_MCKESSON_JOB_FAMILY_FACET_ID", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "JOB_SCRAPER_MCKESSON_COUNTRY_FACET_ID", "cccccccccccccccccccccccccccccccc"))))
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);
                    assertThat(properties.getMckessonStateFacetId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(properties.getMckessonJobFamilyFacetId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
                    assertThat(properties.getMckessonCountryFacetId()).isEqualTo("cccccccccccccccccccccccccccccccc");
                });
    }

    @Test
    void bindsCvsFacetsFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_CVS_JOB_FAMILY_FACET_ID", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "JOB_SCRAPER_CVS_LOCATION_FACET_IDS",
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb,cccccccccccccccccccccccccccccccc"))))
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);
                    assertThat(properties.getCvsJobFamilyFacetId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(properties.getCvsLocationFacetIds()).containsExactly(
                            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccc");
                });
    }

    @Test
    void bindsNewWorkdayFacetsFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_FROST_BANK_JOB_FAMILY_FACET_ID", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "JOB_SCRAPER_FROST_BANK_LOCATION_FACET_IDS",
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb,cccccccccccccccccccccccccccccccc",
                        "JOB_SCRAPER_CITI_JOB_FAMILY_FACET_ID", "dddddddddddddddddddddddddddddddd",
                        "JOB_SCRAPER_CITI_WORKER_SUBTYPE_FACET_ID", "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                        "JOB_SCRAPER_CITI_COUNTRY_FACET_ID", "ffffffffffffffffffffffffffffffff",
                        "JOB_SCRAPER_CITI_STATE_FACET_ID", "11111111111111111111111111111111",
                        "JOB_SCRAPER_FANNIE_MAE_REMOTE_TYPE_FACET_ID", "22222222222222222222222222222222",
                        "JOB_SCRAPER_FANNIE_MAE_JOB_FAMILY_FACET_ID", "33333333333333333333333333333333",
                        "JOB_SCRAPER_FANNIE_MAE_LOCATION_FACET_ID", "44444444444444444444444444444444"))))
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);
                    assertThat(properties.getFrostBankJobFamilyFacetId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(properties.getFrostBankLocationFacetIds()).containsExactly(
                            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccc");
                    assertThat(properties.getCitiJobFamilyFacetId()).isEqualTo("dddddddddddddddddddddddddddddddd");
                    assertThat(properties.getCitiWorkerSubtypeFacetId()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
                    assertThat(properties.getCitiCountryFacetId()).isEqualTo("ffffffffffffffffffffffffffffffff");
                    assertThat(properties.getCitiStateFacetId()).isEqualTo("11111111111111111111111111111111");
                    assertThat(properties.getFannieMaeRemoteTypeFacetId()).isEqualTo("22222222222222222222222222222222");
                    assertThat(properties.getFannieMaeJobFamilyFacetId()).isEqualTo("33333333333333333333333333333333");
                    assertThat(properties.getFannieMaeLocationFacetId()).isEqualTo("44444444444444444444444444444444");
                });
    }

    @Test
    void bindsHumanaFacetsFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_HUMANA_JOB_FAMILY_FACET_IDS",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))))
                .run(context -> assertThat(context.getBean(JobScraperProperties.class).getHumanaJobFamilyFacetIds())
                        .containsExactly("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    }

    @Test
    void bindsCapitalOneFacetsFromEnvironmentSettings() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", Map.of(
                        "JOB_SCRAPER_CAPITAL_ONE_WORKER_SUBTYPE_FACET_ID", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "JOB_SCRAPER_CAPITAL_ONE_TIME_TYPE_FACET_ID", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "JOB_SCRAPER_CAPITAL_ONE_LOCATION_FACET_IDS",
                        "cccccccccccccccccccccccccccccccc,dddddddddddddddddddddddddddddddd",
                        "JOB_SCRAPER_CAPITAL_ONE_JOB_FAMILY_FACET_IDS",
                        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee,ffffffffffffffffffffffffffffffff"))))
                .run(context -> {
                    JobScraperProperties properties = context.getBean(JobScraperProperties.class);
                    assertThat(properties.getCapitalOneWorkerSubtypeFacetId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(properties.getCapitalOneTimeTypeFacetId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
                    assertThat(properties.getCapitalOneLocationFacetIds()).containsExactly(
                            "cccccccccccccccccccccccccccccccc", "dddddddddddddddddddddddddddddddd");
                    assertThat(properties.getCapitalOneJobFamilyFacetIds()).containsExactly(
                            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "ffffffffffffffffffffffffffffffff");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JobScraperProperties.class)
    static class TestConfiguration {
    }
}
