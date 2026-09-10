package com.jobscraper.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobscraper.application.command.DailyScrapeCommandProducer;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * HTTP-triggered function that publishes {@code job.scrape.commands} on demand, in addition to
 * the existing {@link DailyScrapeCommandProducerFunction} timer trigger.
 *
 * <p>Both triggers delegate to the same {@link DailyScrapeCommandProducer} application service and
 * publish the identical {@code ScrapeCompanyCommand} shape, so every downstream Kafka consumer
 * (scraper, normalizer, persistence, metrics, email) behaves identically regardless of whether the
 * run was started by the daily cron schedule or manually via this endpoint.</p>
 *
 * <p><b>Security:</b> this function uses {@link AuthorizationLevel#FUNCTION}, so every request must
 * include a valid function key (query string {@code ?code=...} or header {@code x-functions-key}).
 * The Functions host enforces this before {@link #run} is ever invoked, both locally (Azure Functions
 * Core Tools issues local keys) and in Azure (function-specific or host keys). See the README section
 * "Triggering a scrape: cron vs. on demand" for exact local key retrieval and usage instructions.</p>
 */
@Component
public class OnDemandScrapeCommandProducerFunction {

    private final DailyScrapeCommandProducer commandProducer;
    private final ObjectMapper objectMapper;

    public OnDemandScrapeCommandProducerFunction(DailyScrapeCommandProducer commandProducer, ObjectMapper objectMapper) {
        this.commandProducer = commandProducer;
        this.objectMapper = objectMapper;
    }

    @FunctionName("OnDemandScrapeCommandProducer")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "scrape/trigger")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String companyId = request.getQueryParameters().get("companyId");

        try {
            if (companyId == null || companyId.isBlank()) {
                int commandsPublished = commandProducer.publishDailyCommands();
                context.getLogger().info(
                        "On-demand scrape triggered for all enabled companies: " + commandsPublished + " command(s) published.");
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(successBody(null, commandsPublished))
                        .build();
            }

            Optional<String> runId = commandProducer.publishCommand(companyId);
            if (runId.isEmpty()) {
                context.getLogger().warning("On-demand scrape rejected: unknown or disabled companyId '" + companyId + "'.");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(errorBody("Unknown or disabled companyId: " + companyId))
                        .build();
            }

            context.getLogger().info("On-demand scrape triggered for company '" + companyId + "', runId=" + runId.get());
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(successBody(runId.get(), 1))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("On-demand scrape trigger failed: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(errorBody("Failed to publish scrape command(s)."))
                    .build();
        }
    }

    private String successBody(String runId, int companiesPublished) {
        ObjectNode node = objectMapper.createObjectNode();
        if (runId != null) {
            node.put("runId", runId);
        }
        node.put("companiesPublished", companiesPublished);
        return node.toString();
    }

    private String errorBody(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", message);
        return node.toString();
    }
}

