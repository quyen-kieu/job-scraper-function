package com.jobscraper.function;

import com.jobscraper.application.persistence.NormalizedPostingPersistenceHandler;
import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaTrigger;
import org.springframework.stereotype.Component;

/**
 * Kafka-triggered function for the {@code job.postings.normalized} topic.
 *
 * <p>This is the dedicated persistence boundary for the platform. Cosmos DB writes for
 * {@code job-postings} and {@code job-observations} happen here via
 * {@link NormalizedPostingPersistenceHandler}, not in {@link NormalizerConsumerFunction}.</p>
 *
 * <p>Checkpoint semantics: Azure Kafka trigger checkpointing occurs only after
 * {@link #run(String, ExecutionContext)} returns without throwing. If persistence fails, the
 * handler throws so the offset is not committed and the message is redelivered.</p>
 */
@Component
public class PersistenceConsumerFunction {

    private final NormalizedPostingPersistenceHandler persistenceHandler;

    public PersistenceConsumerFunction(NormalizedPostingPersistenceHandler persistenceHandler) {
        this.persistenceHandler = persistenceHandler;
    }

    @FunctionName("PersistenceConsumerFunction")
    public void run(
            @KafkaTrigger(
                    name = "message",
                    topic = "%KAFKA_POSTINGS_NORMALIZED_TOPIC%",
                    brokerList = "%KAFKA_BOOTSTRAP_SERVERS%",
                    consumerGroup = "%KAFKA_PERSISTENCE_CONSUMER_GROUP%",
                    username = "%KAFKA_USERNAME%",
                    password = "%KAFKA_PASSWORD%",
                    authenticationMode = BrokerAuthenticationMode.PLAIN,
                    protocol = BrokerProtocol.SASLSSL,
                    cardinality = Cardinality.ONE,
                    dataType = "string")
            String message,
            final ExecutionContext context) throws Exception {
        context.getLogger().info("PersistenceConsumerFunction received a job.postings.normalized message.");
        handlePersistenceEvent(message, context);
        // No explicit commit call exists in the Azure Kafka Java binding: reaching this point
        // without an exception is what causes the extension to checkpoint the offset.
    }

    /**
     * Handles a single normalized posting event by delegating to the persistence handler.
     *
     * <p>If this method throws, it propagates out of {@link #run(String, ExecutionContext)},
     * which prevents the Kafka trigger from checkpointing the offset, allowing redelivery.</p>
     *
     * @param message the raw JSON message from Kafka
     * @param context the Azure execution context
     * @throws Exception any exception from deserialization or persistence (triggers redelivery)
     */
    private void handlePersistenceEvent(String message, ExecutionContext context) throws Exception {
        String payloadMessage = KafkaMessageSupport.unwrap(message);
        var posting = persistenceHandler.handle(payloadMessage);
        context.getLogger().info("Persisted posting " + posting.id());
    }
}
