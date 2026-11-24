package com.jobscraper.application.persistence;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCompletedEvent;
import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.jobscraper.domain.job.DescriptionFormat;
import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.CosmosDescriptionHandoffStorage;
import com.jobscraper.infrastructure.cosmos.CosmosJobPostingRepository;
import com.jobscraper.infrastructure.cosmos.JobObservationRepository;
import com.jobscraper.scraper.api.ScrapedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "JOB_SCRAPER_VERIFY_COSMOS_HANDOFF", matches = "true")
class LiveCosmosPostingPersistenceTest {
    @Test
    void persistsBothDescriptionFieldsWithoutBlobStorage() throws Exception {
        try (var client = new CosmosClientBuilder()
                .endpoint(System.getenv("AZURE_COSMOS_ENDPOINT"))
                .key(System.getenv("AZURE_COSMOS_KEY"))
                .gatewayMode()
                .buildClient()) {
            var container = client.getDatabase(System.getenv("AZURE_COSMOS_DATABASE_NAME"))
                    .getContainer(System.getenv("AZURE_COSMOS_DESCRIPTION_HANDOFF_CONTAINER"));
            var handoffs = new CosmosDescriptionHandoffStorage(container);
            var postings = new CosmosJobPostingRepository(container);
            var objectMapper = new ObjectMapper().findAndRegisterModules();
            String company = "handoff-verification";
            String run = UUID.randomUUID().toString();
            String jobId = UUID.randomUUID().toString();
            String postingId = NormalizedJobPosting.buildId(company, jobId);
            String html = "<p>Build <b>caf\u00e9</b> systems.</p>";
            String reference = handoffs.writeDescription(company, run, jobId, html);
            try {
                var job = new ScrapedJob(jobId, "Engineer", "Texas", null, "https://example.com/job",
                        null, reference, DescriptionFormat.HTML);
                var event = new ScrapeCompletedEvent(run, company, Instant.now(), Instant.now(),
                        1, 1, List.of(), List.of(), List.of(job));
                List<NormalizedJobPosting> messages = new ArrayList<>();
                new ScrapeCompletedNormalizationHandler(objectMapper, messages::add, postings, ignored -> {})
                        .handle(objectMapper.writeValueAsString(event));
                assertThat(messages).hasSize(1);
                var handler = new NormalizedPostingPersistenceHandler(
                        objectMapper, postings, mock(JobObservationRepository.class), handoffs);
                handler.handle(objectMapper.writeValueAsString(messages.get(0)));

                JsonNode saved = container.readItem(postingId, new PartitionKey(company), JsonNode.class).getItem();
                assertThat(saved.path("descriptionHtml").asText()).isEqualTo(html);
                assertThat(saved.path("descriptionPlainText").asText()).isEqualTo("Build caf\u00e9 systems.");
                assertThat(saved.has("description")).isFalse();
                assertThat(saved.has("descriptionPath")).isFalse();
                assertThat(saved.has("descriptionFormat")).isFalse();
            } finally {
                try {
                    container.deleteItem(postingId, new PartitionKey(company), new CosmosItemRequestOptions());
                } catch (com.azure.cosmos.CosmosException ex) {
                    if (ex.getStatusCode() != 404) {
                        throw ex;
                    }
                } finally {
                    container.deleteItem(reference, new PartitionKey(company), new CosmosItemRequestOptions());
                }
            }
        }
    }
}
