package com.jobscraper.application.persistence;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.CosmosDescriptionHandoffStorage;
import com.jobscraper.scraper.api.JobDescriptionText;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_LIVE_SCRAPES", matches = "true")
class LiveScrapeVerificationTest {
    @Test
    void everyDescriptionFromTheRequestedRunsIsPresentInCosmos() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Map<String, String> expected = Map.of(
                "mckesson", required("RUN_MCKESSON"),
                "gm-financial", required("RUN_GM_FINANCIAL"),
                "first-citizens", required("RUN_FIRST_CITIZENS"),
                "frost-bank", required("RUN_FROST_BANK"));
        Map<String, ScrapeCompletedEvent> events = readCompletedEvents(mapper, expected);
        assertThat(events.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());

        try (var client = new CosmosClientBuilder()
                .endpoint(required("AZURE_COSMOS_ENDPOINT"))
                .key(required("AZURE_COSMOS_KEY"))
                .gatewayMode()
                .buildClient()) {
            var database = client.getDatabase(required("AZURE_COSMOS_DATABASE_NAME"));
            var handoffs = new CosmosDescriptionHandoffStorage(
                    database.getContainer(required("AZURE_COSMOS_DESCRIPTION_HANDOFF_CONTAINER")));
            CosmosContainer postings = database.getContainer(required("AZURE_COSMOS_POSTINGS_CONTAINER"));
            for (var entry : events.entrySet()) {
                var event = entry.getValue();
                System.out.printf("%s: jobs=%d errors=%d%n", entry.getKey(), event.jobs().size(), event.errors().size());
                for (String error : event.errors()) {
                    String category = error.startsWith("Detail fetch failed") ? "detail fetch"
                            : error.startsWith("Missing description") ? "missing description"
                            : error.startsWith("Search page") ? "search page"
                            : error.contains("storage") ? "storage"
                            : "other";
                    var http = java.util.regex.Pattern.compile("HTTP \\d{3}").matcher(error);
                    boolean hasHttpStatus = http.find();
                    String reason = error.contains("Invalid Workday search response") ? " invalid Workday response"
                            : error.contains("empty page before") ? " empty page before total"
                            : error.contains("missing a valid title, job ID or path") ? " invalid search posting"
                            : error.contains("timed out") ? " timeout"
                            : error.contains("storage write failed") ? " storage write failed"
                            : "";
                    System.out.printf("%s error: %s%s%n", entry.getKey(), category,
                            hasHttpStatus ? " " + http.group() : reason);
                }
                assertThat(event.errors()).as("scrape error count for %s", entry.getKey()).isEmpty();
                assertThat(event.jobs()).as("jobs for %s", entry.getKey()).isNotEmpty();
                for (var job : event.jobs()) {
                    assertThat(job.descriptionPath()).as("description handoff for %s job", entry.getKey()).isNotBlank();
                    String source = handoffs.readDescription(entry.getKey(), job.externalJobId(), job.descriptionPath());
                    JsonNode posting = postings.readItem(
                            NormalizedJobPosting.buildId(entry.getKey(), job.externalJobId()),
                            new PartitionKey(entry.getKey()), JsonNode.class).getItem();
                    assertThat(posting.path("descriptionPlainText").asText())
                            .isEqualTo(JobDescriptionText.fromHtml(source));
                    if (job.descriptionFormat() == DescriptionFormat.HTML) {
                        assertThat(posting.path("descriptionHtml").asText()).isEqualTo(source);
                    } else {
                        assertThat(posting.path("descriptionHtml").isNull()
                                || posting.path("descriptionHtml").isMissingNode()).isTrue();
                    }
                    assertThat(posting.has("description")).isFalse();
                    assertThat(posting.has("descriptionPath")).isFalse();
                }
            }
        }
    }

    private static Map<String, ScrapeCompletedEvent> readCompletedEvents(
            ObjectMapper mapper, Map<String, String> expected) throws Exception {
        Properties config = new Properties();
        config.put("bootstrap.servers", required("KAFKA_BOOTSTRAP_SERVERS"));
        config.put("security.protocol", required("KAFKA_SECURITY_PROTOCOL"));
        config.put(SaslConfigs.SASL_MECHANISM, required("KAFKA_SASL_MECHANISM"));
        config.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                        + escape(required("KAFKA_USERNAME")) + "\" password=\""
                        + escape(required("KAFKA_PASSWORD")) + "\";");
        config.put("key.deserializer", StringDeserializer.class.getName());
        config.put("value.deserializer", StringDeserializer.class.getName());
        config.put("group.id", "job-scraper-verification-" + UUID.randomUUID());
        config.put("enable.auto.commit", false);
        config.put("default.api.timeout.ms", "30000");
        Map<String, ScrapeCompletedEvent> result = new HashMap<>();
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            String topic = required("KAFKA_SCRAPE_COMPLETED_TOPIC");
            List<TopicPartition> partitions = new ArrayList<>();
            consumer.partitionsFor(topic).forEach(p -> partitions.add(new TopicPartition(topic, p.partition())));
            assertThat(partitions).isNotEmpty();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofSeconds(2))) {
                    JsonNode json = mapper.readTree(record.value());
                    String company = json.path("companyId").asText();
                    if (expected.containsKey(company)
                            && expected.get(company).equals(json.path("runId").asText())) {
                        result.put(company, mapper.treeToValue(json, ScrapeCompletedEvent.class));
                    }
                }
                boolean atEnd = true;
                for (var partition : partitions) {
                    atEnd &= consumer.position(partition) >= ends.get(partition);
                }
                if (atEnd) {
                    break;
                }
            }
        }
        return result;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
