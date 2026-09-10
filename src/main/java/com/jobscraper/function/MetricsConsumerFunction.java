package com.jobscraper.function;

import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaTrigger;
import org.springframework.stereotype.Component;

import com.jobscraper.application.metrics.MonthlyMetricAggregationHandler;

/**
 * Kafka-triggered function for the {@code job.postings.normalized} topic, dedicated to the
 * monthly metrics consumer group so it receives its own copy of every normalized posting
 * independently of {@link PersistenceConsumerFunction}.
 *
 * <p>Checkpoint semantics: the Azure Kafka extension only commits the consumed offset after
 * {@link #run(String, ExecutionContext)} returns without throwing.</p>
 */
@Component
public class MetricsConsumerFunction {

    private final MonthlyMetricAggregationHandler aggregationHandler;

    public MetricsConsumerFunction(MonthlyMetricAggregationHandler aggregationHandler) {
        this.aggregationHandler = aggregationHandler;
    }

    @FunctionName("MetricsConsumerFunction")
    public void run(
            @KafkaTrigger(
                    name = "message",
                    topic = "%KAFKA_POSTINGS_NORMALIZED_TOPIC%",
                    brokerList = "%KAFKA_BOOTSTRAP_SERVERS%",
                    consumerGroup = "%KAFKA_METRICS_CONSUMER_GROUP%",
                    username = "%KAFKA_USERNAME%",
                    password = "%KAFKA_PASSWORD%",
                    authenticationMode = BrokerAuthenticationMode.PLAIN,
                    protocol = BrokerProtocol.SASLSSL,
                    cardinality = Cardinality.ONE,
                    dataType = "string")
            String message,
            final ExecutionContext context) throws Exception {
        context.getLogger().info("MetricsConsumerFunction received a job.postings.normalized message.");
        handleNormalizedPostingForMetrics(message, context);
    }

    /**
     * Handles one normalized posting for monthly aggregation.
     *
     * <p>If this method throws, the exception propagates out of {@link #run(String, ExecutionContext)},
     * preventing Kafka offset checkpointing and allowing redelivery.</p>
     */
    private void handleNormalizedPostingForMetrics(String message, ExecutionContext context) throws Exception {
        String payloadMessage = KafkaMessageSupport.unwrap(message);
        var metric = aggregationHandler.handle(payloadMessage);
        context.getLogger().info(
                "Updated monthly metric " + metric.id()
                        + " postingCount=" + metric.postingCount()
                        + " uniquePostingCount=" + metric.uniquePostingCount()
        );
    }
}
