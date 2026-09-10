package com.jobscraper.application.command;

import com.jobscraper.function.JobScraperProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DailyScrapeCommandProducerTest {

    @Test
    void publishesOneCommandPerEnabledCompany() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(
                company("usaa", true),
                company("oracle", true),
                company("indeed", false)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, clock);

        int count = producer.publishDailyCommands();

        assertThat(count).isEqualTo(2);
        assertThat(publisher.commands).hasSize(2);
        assertThat(publisher.commands).extracting(ScrapeCompanyCommand::companyId)
                .containsExactly("usaa", "oracle");
        assertThat(publisher.commands).extracting(ScrapeCompanyCommand::requestedAt)
                .containsOnly(Instant.parse("2026-08-16T12:00:00Z"));
        assertThat(publisher.commands).extracting(ScrapeCompanyCommand::runId)
                .allSatisfy(runId -> assertThat(runId).isNotBlank());
        assertThat(Set.copyOf(publisher.commands.stream().map(ScrapeCompanyCommand::runId).toList()))
                .hasSize(1);
    }

    @Test
    void publishesNothingWhenNoEnabledCompaniesExist() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(company("usaa", false), company("", true)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, clock);

        int count = producer.publishDailyCommands();

        assertThat(count).isZero();
        assertThat(publisher.commands).isEmpty();
    }

    @Test
    void publishesSingleCommandForEnabledCompany() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(company("mckesson", true), company("oracle", false)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T20:00:00Z"), ZoneOffset.UTC);

        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, clock);

        Optional<String> runId = producer.publishCommand("mckesson");

        assertThat(runId).isPresent();
        assertThat(publisher.commands).hasSize(1);
        assertThat(publisher.commands.get(0).companyId()).isEqualTo("mckesson");
        assertThat(publisher.commands.get(0).runId()).isEqualTo(runId.get());
        assertThat(publisher.commands.get(0).requestedAt()).isEqualTo(Instant.parse("2026-08-23T20:00:00Z"));
    }

    @Test
    void doesNotPublishForDisabledCompany() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(company("oracle", false)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, Clock.systemUTC());

        Optional<String> runId = producer.publishCommand("oracle");

        assertThat(runId).isEmpty();
        assertThat(publisher.commands).isEmpty();
    }

    @Test
    void doesNotPublishForUnknownCompany() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(company("mckesson", true)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, Clock.systemUTC());

        Optional<String> runId = producer.publishCommand("does-not-exist");

        assertThat(runId).isEmpty();
        assertThat(publisher.commands).isEmpty();
    }

    @Test
    void doesNotPublishForBlankCompanyId() {
        JobScraperProperties properties = new JobScraperProperties();
        properties.setCompanies(List.of(company("mckesson", true)));

        FakeScrapeCommandPublisher publisher = new FakeScrapeCommandPublisher();
        DailyScrapeCommandProducer producer = new DailyScrapeCommandProducer(properties, publisher, Clock.systemUTC());

        Optional<String> runId = producer.publishCommand(" ");

        assertThat(runId).isEmpty();
        assertThat(publisher.commands).isEmpty();
    }

    private static JobScraperProperties.CompanyProperties company(String id, boolean enabled) {
        JobScraperProperties.CompanyProperties company = new JobScraperProperties.CompanyProperties();
        company.setId(id);
        company.setEnabled(enabled);
        return company;
    }

    private static final class FakeScrapeCommandPublisher implements ScrapeCommandPublisher {
        private final List<ScrapeCompanyCommand> commands = new ArrayList<>();

        @Override
        public void publish(ScrapeCompanyCommand command) {
            commands.add(command);
        }
    }
}

