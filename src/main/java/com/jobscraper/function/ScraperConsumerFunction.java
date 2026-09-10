package com.jobscraper.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscraper.application.command.ScrapeCommandHandler;
import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaTrigger;
import org.springframework.stereotype.Component;

/**
 * Kafka-triggered function for the {@code job.scrape.commands} topic.
 *
 * <p>Receives deserialized {@link com.jobscraper.application.command.ScrapeCompanyCommand}
 * messages and delegates to {@link ScrapeCommandHandler} for routing to the appropriate
 * {@link com.jobscraper.scraper.api.CompanyScraper} implementation.</p>
 *
 * <p>Checkpoint semantics: Azure's Kafka extension commits the consumed offset only after
 * this function's {@link #run(String, ExecutionContext)} method returns without throwing.
 * If {@link #handleScrapeCommand} throws, the offset is not committed and the message is
 * redelivered by the broker (automatic retry behavior).</p>
 */
@Component
public class ScraperConsumerFunction {

    private final ScrapeCommandHandler commandHandler;
    private final ObjectMapper objectMapper;

    public ScraperConsumerFunction(ScrapeCommandHandler commandHandler, ObjectMapper objectMapper) {
        this.commandHandler = commandHandler;
        this.objectMapper = objectMapper;
    }

    @FunctionName("ScraperConsumerFunction")
    public void run(
            @KafkaTrigger(
                    name = "message",
                    topic = "%KAFKA_SCRAPE_COMMANDS_TOPIC%",
                    brokerList = "%KAFKA_BOOTSTRAP_SERVERS%",
                    consumerGroup = "%KAFKA_SCRAPER_CONSUMER_GROUP%",
                    username = "%KAFKA_USERNAME%",
                    password = "%KAFKA_PASSWORD%",
                    authenticationMode = BrokerAuthenticationMode.PLAIN,
                    protocol = BrokerProtocol.SASLSSL,
                    cardinality = Cardinality.ONE,
                    dataType = "string")
            String message,
            final ExecutionContext context) throws Exception {
        context.getLogger().info("ScraperConsumerFunction received a job.scrape.commands message.");
        handleScrapeCommand(message, context);
    }

    /**
     * Handles a single scrape command by delegating to the command handler.
     *
     * <p>If this method throws an exception, it propagates out of {@link #run(String, ExecutionContext)},
     * which prevents the Kafka trigger from checkpointing the offset, allowing redelivery.</p>
     *
     * @param message the raw JSON message from Kafka
     * @param context the Azure execution context
     * @throws Exception any exception from deserialization or scraping (triggers redelivery)
     */
    private void handleScrapeCommand(String message, ExecutionContext context) throws Exception {
        String payloadMessage = KafkaMessageSupport.unwrap(message);
        if (payloadMessage == null || payloadMessage.isBlank()) {
            context.getLogger().warning("Skipping empty job.scrape.commands payload.");
            return;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadMessage);
        } catch (Exception ex) {
            context.getLogger().warning("Skipping malformed job.scrape.commands payload: " + message);
            return;
        }

        String runId = payload.path("runId").asText(null);
        String companyId = payload.path("companyId").asText(null);
        String requestedAt = payload.path("requestedAt").asText(null);
        if (payload == null || runId == null || runId.isBlank() || companyId == null || companyId.isBlank() || requestedAt == null || requestedAt.isBlank()) {
            context.getLogger().warning("Skipping invalid job.scrape.commands payload: missing or blank runId/companyId/requestedAt. Payload=" + payloadMessage);
            return;
        }

        var result = commandHandler.handle(payloadMessage);
        context.getLogger().info(
                String.format(
                        "Scrape completed for company %s: %d search pages, %d detail pages",
                        result.companyId(),
                        result.searchPagesFetched(),
                        result.detailPagesFetched()
                )
        );
    }
}
