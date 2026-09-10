package com.jobscraper.function;

import com.jobscraper.application.normalization.ScrapeCompletedNormalizationHandler;
import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaTrigger;
import org.springframework.stereotype.Component;

/**
 * Kafka-triggered function for the {@code job.scrape.completed} topic.
 *
 * <p>Responsible only for canonicalization and downstream event publication via
 * {@link ScrapeCompletedNormalizationHandler}. Durable Cosmos DB persistence is intentionally
 * not performed here; that responsibility is delegated to {@link PersistenceConsumerFunction},
 * which consumes {@code job.postings.normalized}.</p>
 *
 * <p>Stage 5 applies concrete title/level normalization rules in
 * {@link ScrapeCompletedNormalizationHandler}. This function remains the sole owner of
 * canonicalization logic; metrics consumers aggregate already-normalized fields.</p>
 *
 * <p>Checkpoint semantics: the Azure Kafka extension only commits the consumed offset after
 * {@link #run(String, ExecutionContext)} returns without throwing.</p>
 */
@Component
public class NormalizerConsumerFunction {

    private final ScrapeCompletedNormalizationHandler normalizationHandler;

    public NormalizerConsumerFunction(ScrapeCompletedNormalizationHandler normalizationHandler) {
        this.normalizationHandler = normalizationHandler;
    }

    @FunctionName("NormalizerConsumerFunction")
    public void run(
            @KafkaTrigger(
                    name = "message",
                    topic = "%KAFKA_SCRAPE_COMPLETED_TOPIC%",
                    brokerList = "%KAFKA_BOOTSTRAP_SERVERS%",
                    consumerGroup = "%KAFKA_NORMALIZER_CONSUMER_GROUP%",
                    username = "%KAFKA_USERNAME%",
                    password = "%KAFKA_PASSWORD%",
                    authenticationMode = BrokerAuthenticationMode.PLAIN,
                    protocol = BrokerProtocol.SASLSSL,
                    cardinality = Cardinality.ONE,
                    dataType = "string")
            String message,
            final ExecutionContext context) throws Exception {
        context.getLogger().info("NormalizerConsumerFunction received a job.scrape.completed message.");
        handleScrapeCompletedEvent(message, context);
    }

    /**
     * Handles a single scrape-completed event by delegating to the normalization handler.
     *
     * <p>If this method throws, it propagates out of {@link #run(String, ExecutionContext)},
     * which prevents the Kafka trigger from checkpointing the offset, allowing redelivery.</p>
     *
     * @param message the raw JSON message from Kafka
     * @param context the Azure execution context
     * @throws Exception any exception from deserialization or publishing (triggers redelivery)
     */
    private void handleScrapeCompletedEvent(String message, ExecutionContext context) throws Exception {
        String payloadMessage = KafkaMessageSupport.unwrap(message);
        int publishedCount = normalizationHandler.handle(payloadMessage);
        context.getLogger().info("Published " + publishedCount + " normalized postings.");
    }
}
